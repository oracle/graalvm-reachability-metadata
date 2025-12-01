// In-repo file change filter for GitHub pull request workflows.
//
// Responsibilities:
//   - Accepts a YAML-like input ('patterns') listing glob patterns
//     (supports negation via '!' and wildcards *, **, ?).
//   - Fetches changed files for the current pull request using the GitHub REST API.
//   - Determines if any changed file matches the pattern set (negations are respected).
//   - Emits a single GitHub Action output: 'changed=true' if any match, otherwise 'false'.

const fs = require('fs');
const https = require('https');

function getInput(name, required = false) {
  const key = `INPUT_${name.replace(/ /g, '_').toUpperCase()}`;
  const val = process.env[key];
  if (required && (!val || val.trim() === '')) {
    throw new Error(`Input required and not supplied: ${name}`);
  }
  return val || '';
}

function httpJson(options) {
  return new Promise((resolve, reject) => {
    const req = https.request({ method: 'GET', ...options }, (res) => {
      let data = '';
      res.on('data', (chunk) => (data += chunk));
      res.on('end', () => {
        if (res.statusCode && res.statusCode >= 400) {
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

async function getChangedFilesFromPR() {
  const eventName = process.env.GITHUB_EVENT_NAME || '';
  if (!['pull_request', 'pull_request_target'].includes(eventName)) return [];

  const eventPath = process.env.GITHUB_EVENT_PATH;
  if (!eventPath || !fs.existsSync(eventPath)) return [];

  const payload = JSON.parse(fs.readFileSync(eventPath, 'utf8'));
  const pr = payload.pull_request;
  if (!pr || !pr.number) return [];

  const [owner, repo] = (process.env.GITHUB_REPOSITORY || '').split('/');
  const token = process.env.GITHUB_TOKEN || process.env.GH_TOKEN || process.env.INPUT_GITHUB_TOKEN;
  if (!owner || !repo || !token) return [];

  const files = [];
  let page = 1;
  const perPage = 100;

  while (true) {
    const path = `/repos/${owner}/${repo}/pulls/${pr.number}/files?per_page=${perPage}&page=${page}`;
    const headers = {
      'User-Agent': 'paths-filter-lite',
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

function parsePatterns(yamlLike) {
  const lines = (yamlLike || '').split(/\r?\n/);
  const patterns = [];
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    if (trimmed.startsWith('-')) {
      let pat = trimmed.slice(1).trim();
      if ((pat.startsWith("'") && pat.endsWith("'")) || (pat.startsWith('"') && pat.endsWith('"'))) {
        pat = pat.slice(1, -1);
      }
      patterns.push(pat);
    }
  }
  return patterns;
}

function escapeRegexChar(ch) {
  return ch.replace(/[-/\\^$*+?.()|[\]{}]/g, '\\$&');
}

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

function fileMatches(file, compiled) {
  let included = compiled.hasPositive ? false : true;
  for (const p of compiled.patterns) {
    if (p.rx.test(file)) included = p.negative ? false : true;
  }
  return included;
}

(async function main() {
  try {
    const patternsInput = getInput('file-patterns', true);
    const patterns = parsePatterns(patternsInput);
    const compiled = compilePatterns(patterns);

    const changedFiles = await getChangedFilesFromPR();
    console.log(`Changed files detected (${changedFiles.length}):`);
    changedFiles.forEach(f => console.log(`- ${f}`));

    const matched = changedFiles.some(f => fileMatches(f, compiled));
    fs.appendFileSync(process.env.GITHUB_OUTPUT, `changed=${matched ? 'true' : 'false'}\n`);
    console.log(`Files match filter -> ${matched}`);
  } catch (err) {
    console.log(`paths-filter-lite encountered an error: ${err?.message || err}`);
    process.exit(0);
  }
})();
