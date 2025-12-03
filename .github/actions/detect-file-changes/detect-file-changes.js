// Lightweight in-repository file-change filter for GitHub pull request workflows.
//
// It performs the following:
//   1. Reads a YAML-like input containing glob patterns with support for:
//      - Negation using leading '!'
//      - Wildcards: '*', '**', '?'
//   2. Fetches changed files from the current pull request via the GitHub API
//   3. Matches changed files against the provided patterns
//   4. Outputs `changed=true|false` via GITHUB_OUTPUT.
//
// Example usage in a workflow:
//
//   ```yaml
//   steps:
//     - uses: ./.github/actions/detect-file-changes
//       id: filter
//       with:
//         file-patterns: |
//           - "src/**"
//           - "!src/generated/**"
//   ```
//
// Output:
//   `steps.filter.outputs.changed` = `"true"` if any files match the patterns,
//   `steps.filter.outputs.changed` = `"false"` if no files match the patterns


const fs = require('fs');
const https = require('https');

/**
 * Reads an action input from environment variables.
 * GitHub exposes inputs as: INPUT_<UPPERCASED_NAME>
 */
function getInput(name, required = false) {
  const key = `INPUT_${name.replace(/ /g, '_').toUpperCase()}`;
  const val = process.env[key];
  if (required && (!val || val.trim() === '')) {
    throw new Error(`Input required and not supplied: ${name}`);
  }
  return val || '';
}

/**
 * Performs a GET request and parses JSON.
 */
function httpJson(options) {
  return new Promise((resolve, reject) => {
    const req = https.request({ method: 'GET', ...options }, (res) => {
      let data = '';
      res.on('data', (chunk) => (data += chunk));
      res.on('end', () => {
        if (res.statusCode >= 400) {
          return reject(
            new Error(`HTTP ${res.statusCode} ${res.statusMessage}: ${data || ''}`)
          );
        }
        try {
          resolve(data ? JSON.parse(data) : null);
        } catch (e) {
          reject(new Error(`Failed to parse JSON: ${e.message}. Body: ${data}`));
        }
      });
    });

    req.on('error', reject);
    req.end();
  });
}

/**
 * Returns a list of changed files for the current pull request.
 * Works only when triggered by a 'pull_request' or 'pull_request_target' event.
 */
async function getChangedFilesFromPR() {
  const eventName = process.env.GITHUB_EVENT_NAME || '';
  if (!['pull_request', 'pull_request_target'].includes(eventName)) return [];

  const eventPath = process.env.GITHUB_EVENT_PATH;
  if (!eventPath || !fs.existsSync(eventPath)) return [];

  const payload = JSON.parse(fs.readFileSync(eventPath, 'utf8'));
  const pr = payload?.pull_request;
  if (!pr?.number) return [];

  const [owner, repo] = (process.env.GITHUB_REPOSITORY || '').split('/');
  const token =
    process.env.GITHUB_TOKEN ||
    process.env.GH_TOKEN ||
    process.env.INPUT_GITHUB_TOKEN;

  if (!owner || !repo || !token) return [];

  const files = [];
  let page = 1;
  const perPage = 100;

  while (true) {
    const path = `/repos/${owner}/${repo}/pulls/${pr.number}/files?per_page=${perPage}&page=${page}`;
    const headers = {
      'User-Agent': 'in-repo-path-filter',
      Authorization: `token ${token}`,
      Accept: 'application/vnd.github+json',
    };

    const res = await httpJson({ hostname: 'api.github.com', path, headers });
    if (!Array.isArray(res) || res.length === 0) break;

    files.push(...res.map(f => f.filename).filter(Boolean));

    if (res.length < perPage) break;
    page += 1;
  }

  return files;
}

/**
 * Parses a YAML-style list:
 *
 * Example input:
 *   - "src/**"
 *   - "!src/generated/**"
 */
function parsePatterns(yamlLike) {
  const lines = (yamlLike || '').split(/\r?\n/);
  const patterns = [];

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || !trimmed.startsWith('-')) continue;

    let pat = trimmed.slice(1).trim();

    // Remove surrounding quotes if present
    if (
      (pat.startsWith("'") && pat.endsWith("'")) ||
      (pat.startsWith('"') && pat.endsWith('"'))
    ) {
      pat = pat.slice(1, -1);
    }

    patterns.push(pat);
  }

  return patterns;
}

/**
 * Escapes regex special characters.
 */
function escapeRegexChar(ch) {
  return ch.replace(/[-/\\^$*+?.()|[\]{}]/g, '\\$&');
}

/**
 * Converts a glob pattern to a regular expression.
 * Supports:
 *   *   → any chars except '/'
 *   **  → any chars including '/'
 *   ?   → any one char except '/'
 */
function globToRegex(glob) {
  let re = '';
  for (let i = 0; i < glob.length; i++) {
    const ch = glob[i];
    if (ch === '*') {
      if (glob[i + 1] === '*') {
        re += '.*';
        i++;
      } else {
        re += '[^/]*';
      }
    } else if (ch === '?') {
      re += '[^/]';
    } else {
      re += escapeRegexChar(ch);
    }
  }
  return `^${re}$`;
}

/**
 * Compiles patterns, handling negations.
 */
function compilePatterns(patternsRaw) {
  const patterns = (patternsRaw || []).map(s => {
    let negative = false;
    s = s.trim();

    if (s.startsWith('!')) {
      negative = true;
      s = s.slice(1).trim();
    }

    return { negative, rx: new RegExp(globToRegex(s)) };
  });

  const hasPositive = patterns.some(p => !p.negative);

  return { patterns, hasPositive };
}

/**
 * Determines whether a file matches the compiled pattern list.
 */
function fileMatches(file, compiled) {
  // Default rules:
  //   - If at least one positive pattern exists → default "not included"
  //   - If ONLY negative patterns exist → default "included"
  let included = compiled.hasPositive ? false : true;

  for (const p of compiled.patterns) {
    if (p.rx.test(file)) {
      included = p.negative ? false : true;
    }
  }

  return included;
}

(async function main() {
  try {
    // Read inputs
    const patternsInput = getInput('file-patterns', true);
    const patterns = parsePatterns(patternsInput);
    const compiled = compilePatterns(patterns);

    // Fetch GitHub PR file changes
    const changedFiles = await getChangedFilesFromPR();
    console.log(`Changed files detected (${changedFiles.length}):`);
    changedFiles.forEach(f => console.log(`- ${f}`));

    // Check if any file matches
    const matched = changedFiles.some(f => fileMatches(f, compiled));

    // Write GitHub Action output
    fs.appendFileSync(
      process.env.GITHUB_OUTPUT,
      `changed=${matched ? 'true' : 'false'}\n`
    );

    console.log(`Files match filter → ${matched}`);
  } catch (err) {
    console.log(`file-filter encountered an error: ${err?.message || err}`);
    process.exit(0); // Non-fatal for CI
  }
})();
