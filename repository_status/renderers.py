"""Human and agent report renderers. §FS-repository-status-report.4"""

from __future__ import annotations

import html
import json
from typing import Any


def render_json(report: dict[str, Any]) -> str:
    """Render the stable agent representation."""
    return json.dumps(report, indent=2, ensure_ascii=False) + "\n"


def render_html(report: dict[str, Any]) -> str:
    """Render a self-contained operations-ledger dashboard."""
    flow: dict[str, Any] = report["flow"]
    age_debt_meter: dict[str, Any] = report["ageDebtMeter"]
    tiers: dict[str, dict[str, Any]] = report["tiers"]
    warnings: list[dict[str, Any]] = report["warnings"]
    attention_queue: list[dict[str, Any]] = report["attentionQueue"]

    tier_cards: str = "".join(
        _tier_card(tier_name, tiers[tier_name])
        for tier_name in ("high", "priority", "normal")
    )
    warning_markup: str = _warnings_html(warnings)
    issue_rows: str = "".join(_issue_row(issue) for issue in attention_queue)
    direction: str = flow["direction"]
    balance_percent: float = flow["weightedBalancePercent"]

    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Repository issue status</title>
  <style>
    :root {{
      --bg: #ffffff;
      --ink: #1a1a1a;
      --muted: #6b7280;
      --rule: #e5e7eb;
      --high: #b91c1c;
      --priority: #b45309;
      --normal: #15803d;
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0;
      color: var(--ink);
      background: var(--bg);
      font: 15px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
    }}
    a {{ color: inherit; }}
    .shell {{ width: min(1080px, calc(100% - 40px)); margin: 0 auto; padding: 40px 0 64px; }}
    header {{ margin-bottom: 40px; }}
    h1 {{ margin: 0; font-size: 1.5rem; font-weight: 600; }}
    .repo {{ margin: 0 0 6px; font-size: .8rem; color: var(--muted); }}
    section {{ margin-top: 40px; }}
    h2 {{ margin: 0 0 16px; font-size: 1rem; font-weight: 600; }}
    .tier-grid {{ display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }}
    .tier-card {{ padding: 18px; border: 1px solid var(--rule); border-top: 3px solid var(--tone); border-radius: 6px; }}
    .tier-card.high {{ --tone: var(--high); }}
    .tier-card.priority {{ --tone: var(--priority); }}
    .tier-card.normal {{ --tone: var(--normal); }}
    .tier-top {{ display: flex; justify-content: space-between; align-items: baseline; }}
    .tier-card h3 {{ margin: 0; font-size: .95rem; font-weight: 600; text-transform: capitalize; }}
    .weight {{ font-size: .72rem; color: var(--muted); }}
    .tier-number {{ display: block; margin: 12px 0 2px; font-size: 2rem; font-weight: 600; }}
    .tier-number + span {{ font-size: .8rem; color: var(--muted); }}
    .tier-details {{ display: grid; grid-template-columns: 1fr 1fr; gap: 8px 16px; margin-top: 16px; font-size: .82rem; }}
    .tier-details span {{ color: var(--muted); }}
    .tier-details strong {{ display: block; color: var(--ink); font-weight: 600; margin-top: 1px; }}
    .flow-head {{ display: flex; align-items: baseline; gap: 12px; }}
    .direction-pill {{ font-size: .75rem; font-weight: 600; text-transform: capitalize; color: var(--dir); }}
    .direction-pill.growing {{ --dir: var(--high); }}
    .direction-pill.stable {{ --dir: var(--priority); }}
    .direction-pill.shrinking {{ --dir: var(--normal); }}
    .meter-track {{ position: relative; height: 8px; margin: 16px 0 8px; border-radius: 4px; background: linear-gradient(90deg, var(--high), var(--priority) 50%, var(--normal)); }}
    .meter-marker {{ position: absolute; left: var(--meter); top: 50%; width: 3px; height: 18px; background: var(--ink); transform: translate(-50%, -50%); }}
    .meter-labels {{ display: grid; grid-template-columns: 1fr 1fr 1fr; color: var(--muted); font-size: .72rem; }}
    .meter-labels span:nth-child(2) {{ text-align: center; }}
    .meter-labels span:last-child {{ text-align: right; }}
    .flow-stats {{ display: flex; gap: 32px; margin-top: 20px; }}
    .flow-stat span {{ display: block; color: var(--muted); font-size: .78rem; }}
    .flow-stat strong {{ display: block; margin-top: 2px; font-size: 1.25rem; font-weight: 600; }}
    .debt {{ margin-top: 28px; padding-top: 20px; border-top: 1px solid var(--rule); }}
    .debt-head {{ display: flex; align-items: baseline; gap: 10px; }}
    .debt-head span {{ color: var(--muted); font-size: .78rem; }}
    .debt-head strong {{ font-size: 1.25rem; font-weight: 600; }}
    .debt-track {{ height: 8px; margin: 12px 0 8px; border-radius: 4px; background: var(--rule); overflow: hidden; }}
    .debt-fill {{ height: 100%; width: var(--fill); background: var(--high); border-radius: 4px; }}
    .warnings {{ margin-top: 20px; border: 1px solid var(--rule); border-radius: 6px; }}
    .warning {{ display: grid; grid-template-columns: auto 1fr auto; gap: 12px; padding: 10px 14px; align-items: center; border-bottom: 1px solid var(--rule); font-size: .85rem; }}
    .warning:last-child {{ border-bottom: 0; }}
    .warning code {{ font-size: .75rem; color: var(--priority); }}
    .warning p {{ margin: 0; }}
    .warning strong {{ font-weight: 600; }}
    .section-head {{ display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 16px; }}
    .section-head h2 {{ margin: 0; }}
    .search input {{ width: min(280px, 50vw); border: 1px solid var(--rule); border-radius: 6px; padding: 7px 10px; font-size: .85rem; color: var(--ink); outline: none; }}
    .search input:focus {{ border-color: var(--ink); }}
    table {{ width: 100%; border-collapse: collapse; }}
    th {{ padding: 8px 12px; border-bottom: 1px solid var(--rule); font-size: .72rem; text-align: left; font-weight: 600; color: var(--muted); text-transform: uppercase; letter-spacing: .03em; }}
    td {{ padding: 10px 12px; border-bottom: 1px solid var(--rule); vertical-align: middle; font-size: .88rem; }}
    tbody tr:last-child td {{ border-bottom: 0; }}
    td.num {{ font-variant-numeric: tabular-nums; white-space: nowrap; }}
    .issue-title a {{ text-underline-offset: 2px; }}
    .chip {{ font-size: .72rem; font-weight: 600; text-transform: capitalize; color: var(--chip); }}
    .chip.high {{ --chip: var(--high); }}
    .chip.priority {{ --chip: var(--priority); }}
    .chip.normal {{ --chip: var(--normal); }}
    .chip.age {{ --chip: var(--muted); font-weight: 400; }}
    .empty {{ padding: 20px; color: var(--muted); text-align: center; }}
    @media (max-width: 720px) {{
      .tier-grid {{ grid-template-columns: 1fr; }}
      .flow-stats {{ flex-wrap: wrap; gap: 20px 32px; }}
      table {{ display: block; overflow-x: auto; }}
    }}
  </style>
</head>
<body>
  <main class="shell">
    <header>
      <p class="repo">{_escape(report["repository"])}</p>
      <h1>Priority pressure</h1>
    </header>

    <section>
      <div class="tier-grid">{tier_cards}</div>
    </section>

    <section>
      <div class="flow-head">
        <h2>{flow["windowDays"]}-day flow</h2>
        <span class="direction-pill {_escape(direction)}">{_escape(direction)}</span>
      </div>
      <div class="meter-track" role="meter" aria-label="Weighted flow balance" aria-valuemin="0" aria-valuemax="100" aria-valuenow="{balance_percent:.1f}">
        <div class="meter-marker" style="--meter: {balance_percent:.1f}%"></div>
      </div>
      <div class="meter-labels">
        <span>Growing</span>
        <span>Balanced</span>
        <span>Shrinking</span>
      </div>
      <div class="flow-stats">
        <div class="flow-stat"><span>Weighted opened</span><strong>{flow["weightedOpened"]:,}</strong></div>
        <div class="flow-stat"><span>Weighted resolved</span><strong>{flow["weightedResolved"]:,}</strong></div>
        <div class="flow-stat"><span>Net backlog</span><strong>{flow["weightedNetChange"]:+,}</strong></div>
      </div>

      <div class="debt">
        <div class="debt-head">
          <span>Priority age debt</span>
          <strong>{age_debt_meter["value"]:,}</strong>
        </div>
        <div class="debt-track" role="meter" aria-label="Priority age debt" aria-valuemin="0" aria-valuemax="{age_debt_meter["maximum"]}" aria-valuenow="{age_debt_meter["value"]}">
          <div class="debt-fill" style="--fill: {age_debt_meter["fillPercent"]:.1f}%"></div>
        </div>
        <div class="meter-labels">
          <span>0</span>
          <span></span>
          <span>{age_debt_meter["maximum"]:,}</span>
        </div>
      </div>
      {warning_markup}
    </section>

    <section>
      <div class="section-head">
        <h2>Attention queue</h2>
        <label class="search"><input id="issue-filter" type="search" placeholder="Filter…" aria-label="Filter attention queue"></label>
      </div>
      <table>
        <thead><tr><th>Issue</th><th>Priority</th><th>Age</th><th>Age debt</th><th>Project</th><th>Title</th></tr></thead>
        <tbody id="issue-rows">{issue_rows or '<tr><td colspan="6" class="empty">No unresolved issues.</td></tr>'}</tbody>
      </table>
    </section>
  </main>
  <script>
    const filter = document.getElementById("issue-filter");
    const rows = Array.from(document.querySelectorAll("#issue-rows tr[data-search]"));
    filter.addEventListener("input", () => {{
      const query = filter.value.trim().toLowerCase();
      rows.forEach((row) => {{ row.hidden = !row.dataset.search.includes(query); }});
    }});
  </script>
</body>
</html>
"""


def _tier_card(tier_name: str, tier: dict[str, Any]) -> str:
    """Render one priority tier summary."""
    oldest: str = "—" if tier["oldestAgeDays"] is None else f'{tier["oldestAgeDays"]:,}d'
    return f"""
      <article class="tier-card {_escape(tier_name)}">
        <div class="tier-top"><h3>{_escape(tier_name)}</h3><span class="weight">weight {tier["weight"]}</span></div>
        <strong class="tier-number">{tier["open"]:,}</strong><span>open issues</span>
        <div class="tier-details">
          <span>Age debt<strong>{tier["weightedAgeDebt"]:,}</strong></span>
          <span>Oldest<strong>{oldest}</strong></span>
          <span>Open days<strong>{tier["totalOpenDays"]:,}</strong></span>
          <span>Backlog weight<strong>{tier["weightedBacklog"]:,}</strong></span>
        </div>
      </article>"""


def _warnings_html(warnings: list[dict[str, Any]]) -> str:
    """Render data-quality warnings, or a terse clean result."""
    if not warnings:
        return ""
    items: str = "".join(
        f'<div class="warning"><code>{_escape(warning["code"])}</code>'
        f'<p>{_escape(warning["message"])}</p><strong>{warning["count"]:,}</strong></div>'
        for warning in warnings
    )
    return f'<div class="warnings">{items}</div>'


def _issue_row(issue: dict[str, Any]) -> str:
    """Render one escaped, filterable attention queue row."""
    project_status: str = issue["projectStatus"] or "Unknown"
    search_value: str = " ".join(
        (
            str(issue["number"]),
            issue["title"],
            issue["priority"],
            issue["ageBand"],
            project_status,
            *issue["labels"],
        )
    ).lower()
    return f"""
      <tr data-search="{_escape(search_value)}">
        <td class="num">#{issue["number"]}</td>
        <td><span class="chip {_escape(issue["priority"])}">{_escape(issue["priority"])}</span></td>
        <td class="num">{issue["ageDays"]:,}d <span class="chip age">{_escape(issue["ageBand"])}</span></td>
        <td class="num">{issue["ageDebt"]:,}</td>
        <td>{_escape(project_status)}</td>
        <td class="issue-title"><a href="{_escape(issue["url"])}">{_escape(issue["title"])}</a></td>
      </tr>"""


def _escape(value: Any) -> str:
    """Escape a value for HTML text or a quoted attribute."""
    return html.escape(str(value), quote=True)
