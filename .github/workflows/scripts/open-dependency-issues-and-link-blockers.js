// Opens or reuses GitHub issues for unsupported dependencies discovered in a deps.dev
// expansion and wires them together with GitHub "blocked by" relationships.
//
// It performs the following:
//   1. Loads the raw dependency graph from `DEPS_GRAPH_JSON`
//   2. Aggregates that graph into a GA-level creation plan ordered by dependency depth
//   3. Filters out dependencies already supported by the repository
//   4. Reuses matching open issues when possible, otherwise creates new request issues
//   5. Links created or reused issues using GitHub issue dependency relationships
//   6. Posts a diagnostic comment on the source issue when nothing new must be created
//
// Typical usage:
//   - Invoked from a workflow through `actions/github-script`
//   - The workflow first runs `generateDependencyGraph` and stores its JSON output in
//     `DEPS_GRAPH_JSON`
//   - Optional creation hints can be supplied through:
//     - `CREATE_PATH`, `SUPPORTED_PATH`, `ORDER_GA`
//
// Behavior notes:
//   - Existing open issues are matched by requested Maven coordinates in the issue body or title
//   - When a reusable issue targets a newer version, automation rewrites it to the older version
//     requested by the current plan so blocker creation stays conservative
//   - Duplicate "blocked by" links are treated as a successful no-op
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

/**
 * Resolves the workspace directory used to read repository files and helper scripts.
 */
function resolveWorkspaceDir() {
  return process.env.GITHUB_WORKSPACE || process.cwd();
}

/**
 * Returns whether a GA should never produce an issue because it belongs to GraalVM itself.
 */
function isExcludedGA(ga) {
  const group = String(ga || '').split(':')[0];
  return group === 'org.graalvm' || group.startsWith('org.graalvm.');
}

/**
 * Parses `group:artifact:version` coordinates into convenient lookup fields.
 */
function parseGAV(value) {
  const parts = String(value || '').split(':');
  if (parts.length !== 3 || parts.some((part) => part.length === 0)) {
    return null;
  }

  const [group, artifact, version] = parts;
  return {
    group,
    artifact,
    version,
    ga: `${group}:${artifact}`,
    gav: `${group}:${artifact}:${version}`
  };
}

/**
 * Splits a version into numeric and textual tokens for stable ordering comparisons.
 */
function tokenizeVersion(version) {
  return String(version || '')
    .split(/([0-9]+|[A-Za-z]+)/)
    .filter((token) => token && !/^[.\-+_~]+$/.test(token))
    .map((token) => (/^[0-9]+$/.test(token) ? Number.parseInt(token, 10) : token.toLowerCase()));
}

/**
 * Compares versions using tokenized numeric/text ordering.
 */
function compareVersions(left, right) {
  const leftTokens = tokenizeVersion(left);
  const rightTokens = tokenizeVersion(right);
  const length = Math.max(leftTokens.length, rightTokens.length);

  for (let index = 0; index < length; index++) {
    const leftToken = leftTokens[index];
    const rightToken = rightTokens[index];

    if (leftToken === undefined && rightToken === undefined) {
      return 0;
    }
    if (leftToken === undefined) {
      return -1;
    }
    if (rightToken === undefined) {
      return 1;
    }
    if (leftToken === rightToken) {
      continue;
    }

    if (typeof leftToken === 'number' && typeof rightToken === 'number') {
      return leftToken < rightToken ? -1 : 1;
    }
    if (typeof leftToken === 'number') {
      return 1;
    }
    if (typeof rightToken === 'number') {
      return -1;
    }

    const comparison = leftToken.localeCompare(rightToken);
    if (comparison !== 0) {
      return comparison;
    }
  }

  return String(left || '').localeCompare(String(right || ''));
}

/**
 * Builds the standardized issue title for a requested dependency.
 */
function buildIssueTitle(ga, version) {
  return `Support for ${ga}:${version}`;
}

/**
 * Builds the standardized issue body used for newly created dependency issues.
 */
function buildIssueBody(ga, version) {
  return [
    '### Full Maven coordinates',
    '',
    `${ga}:${version}`,
    '',
    '_This issue was created by automation._'
  ].join('\n');
}

/**
 * Extracts the requested Maven coordinates from the standard issue body template.
 */
function extractCoordinatesFromIssueBody(body) {
  const lines = String(body || '')
    .replace(/\r/g, '')
    .split('\n');
  let foundHeader = false;

  for (const line of lines) {
    if (foundHeader) {
      if (line.trim() === '') {
        continue;
      }
      return line.trim();
    }

    if (/^###[ \t]+Full Maven coordinates[ \t]*$/.test(line)) {
      foundHeader = true;
    }
  }

  return null;
}

/**
 * Parses requested coordinates from an existing issue body first, then from its title.
 */
function parseRequestedCoordinatesFromIssue(issue) {
  const bodyCoordinates = extractCoordinatesFromIssueBody(issue?.body);
  const parsedBodyCoordinates = parseGAV(bodyCoordinates);
  if (parsedBodyCoordinates) {
    return parsedBodyCoordinates;
  }

  const titleMatch = String(issue?.title || '').match(/^Support for ([^:\s]+:[^:\s]+:[^\s]+)$/);
  if (!titleMatch) {
    return null;
  }

  return parseGAV(titleMatch[1]);
}

/**
 * Rewrites the coordinates section of an existing issue body to a requested version.
 */
function updateIssueBodyCoordinates(body, ga, version) {
  const lines = String(body || '')
    .replace(/\r/g, '')
    .split('\n');
  let foundHeader = false;

  for (let index = 0; index < lines.length; index++) {
    if (foundHeader) {
      if (lines[index].trim() === '') {
        continue;
      }
      lines[index] = `${ga}:${version}`;
      return lines.join('\n');
    }

    if (/^###[ \t]+Full Maven coordinates[ \t]*$/.test(lines[index])) {
      foundHeader = true;
    }
  }

  return buildIssueBody(ga, version);
}

/**
 * Loads the raw dependency graph JSON from `DEPS_GRAPH_JSON`.
 */
function loadRawDependencyGraph() {
  const graphJson = String(process.env.DEPS_GRAPH_JSON || '').trim();
  if (graphJson.length === 0) {
    return {};
  }

  return JSON.parse(graphJson);
}

/**
 * Returns whether a value is not a populated plain object.
 */
function isEmptyObject(value) {
  return !value || typeof value !== 'object' || Array.isArray(value) || Object.keys(value).length === 0;
}

/**
 * Detects the raw dependency graph shape emitted before GA-level aggregation.
 */
function isRawGraph(value) {
  return Boolean(
    value &&
      typeof value === 'object' &&
      !Array.isArray(value) &&
      typeof value.root === 'string' &&
      Array.isArray(value.nodes) &&
      value.nodes.every(
        (node) =>
          node &&
          typeof node === 'object' &&
          typeof node.id === 'string' &&
          Array.isArray(node.dependencies)
      )
  );
}

/**
 * Returns a de-duplicated array containing only non-empty strings.
 */
function uniqueStrings(values) {
  return [...new Set((Array.isArray(values) ? values : []).filter((value) => typeof value === 'string' && value.length > 0))];
}

/**
 * Collapses a raw GAV dependency graph into a GA-level plan.
 *
 * For every GA it keeps the minimum encountered version, records GA blockers,
 * derives breadth-first depth from the root, and computes a suggested creation order.
 */
function buildAggregatedPlanFromRawGraph(graph) {
  const root = parseGAV(graph.root);
  if (!root) {
    return {
      plan: {},
      creationOrder: []
    };
  }

  const dependenciesByGAV = new Map();
  const nodeOrder = [];
  const seenNodes = new Set();

  function registerNode(gav) {
    if (typeof gav !== 'string' || gav.length === 0 || seenNodes.has(gav)) {
      return;
    }
    seenNodes.add(gav);
    nodeOrder.push(gav);
  }

  registerNode(graph.root);

  for (const node of graph.nodes || []) {
    if (!node || typeof node.id !== 'string' || node.id.length === 0) {
      continue;
    }

    registerNode(node.id);
    const dependencies = uniqueStrings(node.dependencies);
    dependenciesByGAV.set(node.id, dependencies);

    for (const dependency of dependencies) {
      registerNode(dependency);
    }
  }

  for (const gav of nodeOrder) {
    if (!dependenciesByGAV.has(gav)) {
      dependenciesByGAV.set(gav, []);
    }
  }

  const depthByGAV = new Map([[graph.root, 0]]);
  const queue = [graph.root];
  while (queue.length > 0) {
    const current = queue.shift();
    const currentDepth = depthByGAV.get(current) || 0;
    for (const dependency of dependenciesByGAV.get(current) || []) {
      if (!depthByGAV.has(dependency)) {
        depthByGAV.set(dependency, currentDepth + 1);
        queue.push(dependency);
      }
    }
  }

  const gaMinVersion = new Map();
  for (const gav of nodeOrder) {
    const parsed = parseGAV(gav);
    if (!parsed) {
      continue;
    }

    const currentVersion = gaMinVersion.get(parsed.ga);
    if (!currentVersion || compareVersions(parsed.version, currentVersion) < 0) {
      gaMinVersion.set(parsed.ga, parsed.version);
    }
  }

  const blockedByGA = new Map();
  for (const gav of nodeOrder) {
    const parent = parseGAV(gav);
    if (!parent) {
      continue;
    }

    if (!blockedByGA.has(parent.ga)) {
      blockedByGA.set(parent.ga, new Set());
    }

    for (const dependencyGAV of dependenciesByGAV.get(gav) || []) {
      const child = parseGAV(dependencyGAV);
      if (!child) {
        continue;
      }
      if (parent.ga === child.ga) {
        continue;
      }

      blockedByGA.get(parent.ga).add(child.ga);
      if (!blockedByGA.has(child.ga)) {
        blockedByGA.set(child.ga, new Set());
      }
    }
  }

  for (const ga of gaMinVersion.keys()) {
    if (!blockedByGA.has(ga)) {
      blockedByGA.set(ga, new Set());
    }
  }

  const depthGA = new Map([[root.ga, 0]]);
  const gaQueue = [root.ga];
  while (gaQueue.length > 0) {
    const currentGA = gaQueue.shift();
    const currentDepth = depthGA.get(currentGA) || 0;

    for (const blockerGA of blockedByGA.get(currentGA) || []) {
      if (!depthGA.has(blockerGA)) {
        depthGA.set(blockerGA, currentDepth + 1);
        gaQueue.push(blockerGA);
      }
    }
  }

  const sortedGA = Array.from(gaMinVersion.keys()).sort((left, right) => {
    const depthDifference = (depthGA.get(left) ?? Number.MAX_SAFE_INTEGER) - (depthGA.get(right) ?? Number.MAX_SAFE_INTEGER);
    if (depthDifference !== 0) {
      return depthDifference;
    }
    return left.localeCompare(right);
  });

  const plan = {
    root: root.ga,
    nodes: sortedGA.map((ga) => ({
      ga,
      version: gaMinVersion.get(ga),
      blockedBy: Array.from(blockedByGA.get(ga) || []),
      depth: depthGA.get(ga) ?? Number.MAX_SAFE_INTEGER
    }))
  };

  return {
    plan,
    creationOrder: computeCreationOrder(plan)
  };
}

/**
 * Parses a TSV file describing dependency issues to create.
 *
 * Each line is expected to contain `ga<TAB>version`.
 */
function parseCreateTSV(createTSV) {
  const excludedGA = new Set();
  const toCreate = new Map();

  if (createTSV.length === 0) {
    return { excludedGA, toCreate };
  }

  for (const line of createTSV.split('\n')) {
    const [ga, version] = line.split('\t');
    if (!ga || !version) {
      continue;
    }
    if (isExcludedGA(ga)) {
      excludedGA.add(ga);
      continue;
    }
    toCreate.set(ga, version);
  }

  return { excludedGA, toCreate };
}

/**
 * Loads the curated list of artifacts already tracked by the repository.
 */
function loadListedArtifacts(workspaceDir) {
  const listedArtifactsPath = path.join(
    workspaceDir,
    'metadata',
    'library-and-framework-list.json'
  );
  const listedArtifacts = JSON.parse(fs.readFileSync(listedArtifactsPath, 'utf8'));
  return new Set(
    listedArtifacts
      .map((entry) => entry?.artifact)
      .filter((artifact) => typeof artifact === 'string' && artifact.length > 0)
  );
}

/**
 * Checks whether repository automation already supports the given coordinates.
 */
function isRepositorySupported(workspaceDir, gav) {
  const supportScriptPath = path.join(workspaceDir, 'check-library-support.sh');
  const result = spawnSync('bash', [supportScriptPath, gav], {
    cwd: workspaceDir,
    encoding: 'utf8'
  });

  if (result.error) {
    throw result.error;
  }

  const output = `${result.stdout || ''}${result.stderr || ''}`;
  return output.includes('is supported by the GraalVM Reachability Metadata repository.');
}

/**
 * Derives creation candidates directly from the dependency plan and repository checks.
 */
function prepareCreationInputs(plan, workspaceDir) {
  const supportedGA = new Set();
  const excludedGA = new Set();
  const toCreate = new Map();
  const listedArtifacts = loadListedArtifacts(workspaceDir);

  for (const node of Array.isArray(plan.nodes) ? plan.nodes : []) {
    const ga = node?.ga;
    const version = node?.version;
    if (!ga || !version) {
      continue;
    }

    if (listedArtifacts.has(ga)) {
      supportedGA.add(ga);
      continue;
    }

    const gav = `${ga}:${version}`;
    if (isRepositorySupported(workspaceDir, gav)) {
      supportedGA.add(ga);
      continue;
    }

    if (isExcludedGA(ga)) {
      excludedGA.add(ga);
      continue;
    }

    toCreate.set(ga, version);
  }

  return { excludedGA, supportedGA, toCreate };
}

/**
 * Loads precomputed creation inputs when provided, otherwise derives them from the plan.
 */
function loadCreationInputs(plan, workspaceDir) {
  const createPath = String(process.env.CREATE_PATH || '').trim();
  const supportedPath = String(process.env.SUPPORTED_PATH || '').trim();

  if (createPath.length > 0 && supportedPath.length > 0) {
    const createTSV = fs.readFileSync(createPath, 'utf8').trim();
    const supportedGA = new Set(
      fs.readFileSync(supportedPath, 'utf8')
        .trim()
        .split('\n')
        .filter(Boolean)
    );
    return {
      supportedGA,
      ...parseCreateTSV(createTSV)
    };
  }

  return prepareCreationInputs(plan, workspaceDir);
}

/**
 * Reads an explicit GA creation order override from the environment.
 */
function getExplicitCreationOrderFromEnv() {
  const orderGAraw = (process.env.ORDER_GA || '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);

  return Array.from(
    new Set(orderGAraw.map((value) => value.split(':').slice(0, 2).join(':')))
  );
}

/**
 * Computes a dependency-first creation order from the blocker graph.
 *
 * Nodes with no blockers are emitted first. Remaining cyclic nodes are appended in a
 * deterministic fallback order so issue creation can still proceed.
 */
function computeCreationOrder(plan) {
  const blockedByGA = new Map();
  const parentsByGA = new Map();
  const depCount = new Map();
  const depthGA = new Map();

  for (const node of Array.isArray(plan?.nodes) ? plan.nodes : []) {
    if (!node?.ga) {
      continue;
    }

    const blockers = uniqueStrings(node.blockedBy);
    blockedByGA.set(node.ga, blockers);
    depCount.set(node.ga, blockers.length);

    if (Number.isFinite(node.depth)) {
      depthGA.set(node.ga, node.depth);
    }

    for (const blockerGA of blockers) {
      if (!parentsByGA.has(blockerGA)) {
        parentsByGA.set(blockerGA, new Set());
      }
      parentsByGA.get(blockerGA).add(node.ga);

      if (!depCount.has(blockerGA)) {
        depCount.set(blockerGA, 0);
      }
      if (!blockedByGA.has(blockerGA)) {
        blockedByGA.set(blockerGA, []);
      }
    }
  }

  if (plan?.root && !depCount.has(plan.root)) {
    depCount.set(plan.root, 0);
  }

  const queue = [];
  for (const [ga, count] of depCount.entries()) {
    if (count === 0) {
      queue.push(ga);
    }
  }

  const creationOrder = [];
  while (queue.length > 0) {
    const leaf = queue.shift();
    creationOrder.push(leaf);

    for (const parentGA of parentsByGA.get(leaf) || []) {
      const nextCount = Math.max(0, (depCount.get(parentGA) || 0) - 1);
      depCount.set(parentGA, nextCount);
      if (nextCount === 0) {
        queue.push(parentGA);
      }
    }
  }

  const ordered = new Set(creationOrder);
  const cyclicOrRemaining = Array.from(depCount.keys()).filter((ga) => !ordered.has(ga));
  cyclicOrRemaining.sort((left, right) => {
    const depthDifference = (depthGA.get(right) ?? Number.MIN_SAFE_INTEGER) - (depthGA.get(left) ?? Number.MIN_SAFE_INTEGER);
    if (depthDifference !== 0) {
      return depthDifference;
    }

    const depCountDifference = (depCount.get(left) || 0) - (depCount.get(right) || 0);
    if (depCountDifference !== 0) {
      return depCountDifference;
    }

    return left.localeCompare(right);
  });

  creationOrder.push(...cyclicOrRemaining);
  return creationOrder;
}

/**
 * Opens or reuses dependency issues for unsupported libraries and links blocker relationships.
 */
module.exports = async function openDependencyIssuesAndLinkBlockers({ github, context }) {
  const owner = context.repo.owner;
  const repo = context.repo.repo;
  const sourceIssueNumber = context.issue.number;
  const workspaceDir = resolveWorkspaceDir();

  const rawDependencyGraph = loadRawDependencyGraph();
  if (isEmptyObject(rawDependencyGraph)) {
    console.log('No raw dependency graph was provided in DEPS_GRAPH_JSON. Skipping.');
    return;
  }

  if (!isRawGraph(rawDependencyGraph)) {
    console.log('DEPS_GRAPH_JSON does not contain a valid raw dependency graph. Skipping.');
    return;
  }

  const { plan, creationOrder: computedCreationOrder } = buildAggregatedPlanFromRawGraph(rawDependencyGraph);
  if (isEmptyObject(plan)) {
    console.log('Unable to derive a dependency issue plan from DEPS_GRAPH_JSON. Skipping.');
    return;
  }

  const rootGA = plan.root;
  if (!rootGA) {
    console.log('Dependency issue plan does not define a root GA. Skipping.');
    return;
  }

  const explicitCreationOrder = getExplicitCreationOrderFromEnv();
  const creationOrder = explicitCreationOrder.length > 0
    ? explicitCreationOrder
    : computedCreationOrder.length > 0
      ? computedCreationOrder
      : Array.from(new Set([rootGA, ...(plan.nodes || []).map((node) => node.ga)]));

  const { excludedGA, supportedGA, toCreate } = loadCreationInputs(plan, workspaceDir);
  console.log('Supported GA detected during checks:', Array.from(supportedGA));
  console.log('Creation candidates (GA -> version):', Array.from(toCreate.entries()));
  console.log('Excluded creation candidates (GA):', Array.from(excludedGA));
  console.log('Creation order (GA):', creationOrder);

  const blockedByGA = new Map();
  for (const node of plan.nodes || []) {
    blockedByGA.set(node.ga, Array.isArray(node.blockedBy) ? node.blockedBy.slice() : []);
  }

  const effectiveBlockerMemo = new Map();
  const acceptedBlockersByIssueNumber = new Map();

  /**
   * Returns the direct GA blockers declared by the normalized plan.
   */
  function directBlockers(ga) {
    return [...new Set(blockedByGA.get(ga) || [])];
  }

  /**
   * Expands blockers transitively while skipping blockers already supported or excluded.
   *
   * The resulting list contains only blockers that still need issue relationships.
   */
  function effectiveBlockers(ga) {
    if (effectiveBlockerMemo.has(ga)) {
      return effectiveBlockerMemo.get(ga);
    }

    const seen = new Set();
    const result = [];

    function dfs(currentGA) {
      if (seen.has(currentGA)) {
        return;
      }
      seen.add(currentGA);

      const blockers = blockedByGA.get(currentGA) || [];
      for (const blockerGA of blockers) {
        if (supportedGA.has(blockerGA) || excludedGA.has(blockerGA)) {
          dfs(blockerGA);
        } else {
          result.push(blockerGA);
        }
      }
    }

    dfs(ga);

    const uniqueResult = [...new Set(result)];
    effectiveBlockerMemo.set(ga, uniqueResult);
    return uniqueResult;
  }

  /**
   * Records an accepted blocker edge in the in-memory issue dependency graph.
   */
  function addAcceptedBlocker(blockedIssueNumber, blockerIssueNumber) {
    if (!Number.isInteger(blockedIssueNumber) || !Number.isInteger(blockerIssueNumber)) {
      return;
    }

    if (!acceptedBlockersByIssueNumber.has(blockedIssueNumber)) {
      acceptedBlockersByIssueNumber.set(blockedIssueNumber, new Set());
    }

    acceptedBlockersByIssueNumber.get(blockedIssueNumber).add(blockerIssueNumber);
  }

  /**
   * Lists the issues that currently block a GitHub issue.
   */
  async function listBlockedByIssues(issueNumber) {
    const blockedByIssues = [];
    let page = 1;

    while (true) {
      const response = await github.request(
        'GET /repos/{owner}/{repo}/issues/{issue_number}/dependencies/blocked_by',
        {
          owner,
          repo,
          issue_number: issueNumber,
          page,
          per_page: 100,
          headers: {
            accept: 'application/vnd.github+json',
            'X-GitHub-Api-Version': '2022-11-28'
          }
        }
      );

      const pageItems = Array.isArray(response.data)
        ? response.data
        : Array.isArray(response.data?.blocked_by)
          ? response.data.blocked_by
          : [];

      blockedByIssues.push(...pageItems);

      if (pageItems.length < 100) {
        break;
      }
      page++;
    }

    return blockedByIssues;
  }

  /**
   * Hydrates the in-memory issue dependency graph from existing GitHub blocker links.
   *
   * Traversal is recursive so reused issues cannot hide longer dependency paths that would
   * turn a newly added link into a cycle.
   */
  async function hydrateAcceptedBlockersFromExistingIssues(seedIssueNumbers) {
    const queue = [...new Set(seedIssueNumbers.filter((issueNumber) => Number.isInteger(issueNumber)))];
    const hydratedIssueNumbers = new Set();

    while (queue.length > 0) {
      const issueNumber = queue.shift();
      if (!Number.isInteger(issueNumber) || hydratedIssueNumbers.has(issueNumber)) {
        continue;
      }
      hydratedIssueNumbers.add(issueNumber);

      const blockedByIssues = await listBlockedByIssues(issueNumber);
      for (const blockedByIssue of blockedByIssues) {
        const blockerIssueNumber = blockedByIssue?.number;
        if (!Number.isInteger(blockerIssueNumber)) {
          continue;
        }

        addAcceptedBlocker(issueNumber, blockerIssueNumber);
        if (!hydratedIssueNumbers.has(blockerIssueNumber)) {
          queue.push(blockerIssueNumber);
        }
      }
    }
  }

  /**
   * Detects whether accepting a blocker link would create a cycle among accepted issue links.
   */
  function wouldCreateAcceptedCycle(blockedIssueNumber, blockerIssueNumber) {
    if (blockedIssueNumber === blockerIssueNumber) {
      return true;
    }

    const seen = new Set();
    const stack = [blockerIssueNumber];

    while (stack.length > 0) {
      const currentIssueNumber = stack.pop();
      if (currentIssueNumber === blockedIssueNumber) {
        return true;
      }
      if (seen.has(currentIssueNumber)) {
        continue;
      }
      seen.add(currentIssueNumber);

      for (const acceptedBlockerIssueNumber of acceptedBlockersByIssueNumber.get(currentIssueNumber) || []) {
        if (!seen.has(acceptedBlockerIssueNumber)) {
          stack.push(acceptedBlockerIssueNumber);
        }
      }
    }

    return false;
  }

  const gaIssue = new Map([[rootGA, sourceIssueNumber]]);
  let createdCount = 0;

  const openIssues = await github.paginate(github.rest.issues.listForRepo, {
    owner,
    repo,
    state: 'open',
    per_page: 100
  });
  const reusableIssueByGA = new Map();

  for (const issue of openIssues) {
    if (issue.pull_request || issue.number === sourceIssueNumber) {
      continue;
    }

    const requestedCoordinates = parseRequestedCoordinatesFromIssue(issue);
    if (!requestedCoordinates) {
      continue;
    }

    const existingReusableIssue = reusableIssueByGA.get(requestedCoordinates.ga);
    if (!existingReusableIssue || issue.number < existingReusableIssue.issue.number) {
      reusableIssueByGA.set(requestedCoordinates.ga, {
        issue,
        requestedCoordinates
      });
    }
  }

  for (const ga of creationOrder) {
    if (ga === rootGA || !toCreate.has(ga)) {
      continue;
    }

    const version = toCreate.get(ga);
    const title = buildIssueTitle(ga, version);
    const body = buildIssueBody(ga, version);
    const reusableIssue = reusableIssueByGA.get(ga);
    let issueNumber = null;

    if (reusableIssue) {
      issueNumber = reusableIssue.issue.number;
      const existingVersion = reusableIssue.requestedCoordinates.version;

      if (compareVersions(version, existingVersion) < 0) {
        const updatedBody = updateIssueBodyCoordinates(reusableIssue.issue.body, ga, version);
        await github.rest.issues.update({
          owner,
          repo,
          issue_number: issueNumber,
          title,
          body: updatedBody
        });
        reusableIssue.issue.title = title;
        reusableIssue.issue.body = updatedBody;
        reusableIssue.requestedCoordinates = parseGAV(`${ga}:${version}`);
        console.log(
          `Reused issue #${issueNumber} for ${ga} and updated it from version ${existingVersion} to older version ${version}.`
        );
      } else {
        console.log(
          `Reused existing open issue #${issueNumber} for ${ga} without changing its version (${existingVersion}).`
        );
      }
    } else {
      const created = await github.rest.issues.create({
        owner,
        repo,
        title,
        body,
        labels: ['library-new-request']
      });
      issueNumber = created.data.number;
      createdCount++;
      reusableIssueByGA.set(ga, {
        issue: created.data,
        requestedCoordinates: parseGAV(`${ga}:${version}`)
      });
    }

    gaIssue.set(ga, issueNumber);
  }

  await hydrateAcceptedBlockersFromExistingIssues(Array.from(gaIssue.values()));

  const issueDatabaseIdMemo = new Map();

  /**
   * Resolves and memoizes the numeric database id required by the issue dependency API.
   */
  async function getIssueDatabaseId(issueNumber) {
    if (issueDatabaseIdMemo.has(issueNumber)) {
      return issueDatabaseIdMemo.get(issueNumber);
    }

    const { data } = await github.rest.issues.get({
      owner,
      repo,
      issue_number: issueNumber
    });

    const issueId = data?.id;
    if (!issueId) {
      throw new Error(`Cannot resolve database id for issue #${issueNumber}`);
    }

    issueDatabaseIdMemo.set(issueNumber, issueId);
    return issueId;
  }

  /**
   * Detects duplicate-link failures that should be treated as a successful no-op.
   */
  function isExistingBlockedByLinkError(error) {
    const message = error?.message || String(error);
    const responseData = JSON.stringify(error?.response?.data || {});
    return /already exists|already linked|ALREADY_EXISTS|already a blocker|already blocked/i.test(
      `${message} ${responseData}`
    );
  }

  /**
   * Ensures a GitHub issue dependency link exists between two issue numbers.
   */
  async function ensureBlockedByRelationship(blockedIssueNumber, blockerIssueNumber) {
    const blockerIssueId = await getIssueDatabaseId(blockerIssueNumber);

    try {
      await github.request(
        'POST /repos/{owner}/{repo}/issues/{issue_number}/dependencies/blocked_by',
        {
          owner,
          repo,
          issue_number: blockedIssueNumber,
          issue_id: blockerIssueId,
          headers: {
            accept: 'application/vnd.github+json',
            'X-GitHub-Api-Version': '2022-11-28'
          }
        }
      );
    } catch (error) {
      if (isExistingBlockedByLinkError(error)) {
        return;
      }
      throw error;
    }
  }

  for (const [ga, issueNumber] of gaIssue.entries()) {
    const blockerGAs = ga === rootGA ? directBlockers(ga) : effectiveBlockers(ga);
    const blockingIssues = blockerGAs
      .map((blockerGA) => ({ ga: blockerGA, issueNumber: gaIssue.get(blockerGA) }))
      .filter((value) => Boolean(value.issueNumber));

    if (blockingIssues.length === 0) {
      continue;
    }

    const allowedBlockingIssues = [];
    for (const blockingIssue of blockingIssues) {
      if (wouldCreateAcceptedCycle(issueNumber, blockingIssue.issueNumber)) {
        console.log(
          `Skipping blocker link for ${ga} <- ${blockingIssue.ga} to preserve first-come-first-served ordering and avoid a cycle.`
        );
        continue;
      }
      allowedBlockingIssues.push(blockingIssue);
    }

    if (allowedBlockingIssues.length === 0) {
      continue;
    }

    for (const blockingIssue of allowedBlockingIssues) {
      try {
        await ensureBlockedByRelationship(issueNumber, blockingIssue.issueNumber);
        addAcceptedBlocker(issueNumber, blockingIssue.issueNumber);
      } catch (error) {
        const message = error?.message || String(error);
        console.log(
          `Blocked by relationship failed for #${issueNumber} <- #${blockingIssue.issueNumber}: ${message}`
        );
      }
    }
  }

  if (createdCount === 0) {
    const planNodes = (plan.nodes || []).map((node) => `${node.ga}:${node.version}`).join('\n');
    const supportedList = Array.from(supportedGA).join('\n');
    const diagnosticComment = [
      'Automation: No dependency issues were created by the triage workflow.',
      '',
      'This typically means every dependency in the deps.dev expansion is already supported by the repository or listed in metadata/library-and-framework-list.json.',
      '',
      'Plan nodes:',
      '```',
      planNodes || '(none)',
      '```',
      '',
      'Supported GA detected during checks:',
      '```',
      supportedList || '(none)',
      '```'
    ].join('\n');

    await github.rest.issues.createComment({
      owner,
      repo,
      issue_number: sourceIssueNumber,
      body: diagnosticComment
    });
  }
};
