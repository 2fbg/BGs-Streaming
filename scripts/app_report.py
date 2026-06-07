import os
import time
import json
from datetime import datetime, timezone

import jwt
import requests

app_id = os.environ["GH_APP_ID"]
installation_id = os.environ["GH_INSTALLATION_ID"]
private_key = os.environ["GH_APP_PRIVATE_KEY"]

now = int(time.time())
payload = {
    "iat": now - 60,
    "exp": now + 600,
    "iss": app_id,
}

app_jwt = jwt.encode(payload, private_key, algorithm="RS256")

app_headers = {
    "Accept": "application/vnd.github+json",
    "Authorization": f"Bearer {app_jwt}",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "github-actions-github-app-report",
}

token_response = requests.post(
    f"https://api.github.com/app/installations/{installation_id}/access_tokens",
    headers=app_headers,
    timeout=30,
)
token_response.raise_for_status()
token_data = token_response.json()
installation_token = token_data["token"]

repo_headers = {
    "Accept": "application/vnd.github+json",
    "Authorization": f"Bearer {installation_token}",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "github-actions-github-app-report",
}

repos = []
page = 1

while True:
    repo_response = requests.get(
        "https://api.github.com/installation/repositories",
        headers=repo_headers,
        params={"per_page": 100, "page": page},
        timeout=30,
    )
    repo_response.raise_for_status()
    data = repo_response.json()

    batch = data.get("repositories", [])
    repos.extend(batch)

    if len(batch) < 100:
        break

    page += 1

with open("repositories.json", "w", encoding="utf-8") as f:
    json.dump(
        {
            "total_count": len(repos),
            "repositories": repos,
        },
        f,
        indent=2,
        ensure_ascii=False,
    )

summary = {
    "generated_at_utc": datetime.now(timezone.utc).isoformat(),
    "app_id": app_id,
    "installation_id": installation_id,
    "token_expires_at": token_data.get("expires_at"),
    "total_repositories": len(repos),
    "private_repositories": sum(1 for r in repos if r.get("private")),
    "public_repositories": sum(1 for r in repos if not r.get("private")),
    "token_permissions": token_data.get("permissions", {}),
}

report = {
    "summary": summary,
    "repositories": [
        {
            "id": r.get("id"),
            "name": r.get("name"),
            "full_name": r.get("full_name"),
            "private": r.get("private"),
            "visibility": r.get("visibility"),
            "default_branch": r.get("default_branch"),
            "html_url": r.get("html_url"),
            "owner": (r.get("owner") or {}).get("login"),
            "archived": r.get("archived"),
            "disabled": r.get("disabled"),
            "fork": r.get("fork"),
            "updated_at": r.get("updated_at"),
            "pushed_at": r.get("pushed_at"),
            "permissions": r.get("permissions", {}),
        }
        for r in repos
    ],
}

with open("github-app-access-report.json", "w", encoding="utf-8") as f:
    json.dump(report, f, indent=2, ensure_ascii=False)

lines = []
lines.append("# Relatório de acesso da GitHub App")
lines.append("")
lines.append("## Resumo")
lines.append("")
lines.append(f"- Gerado em UTC: {summary['generated_at_utc']}")
lines.append(f"- App ID: {summary['app_id']}")
lines.append(f"- Installation ID: {summary['installation_id']}")
lines.append(f"- Expiração do token: {summary['token_expires_at']}")
lines.append(f"- Total de repositórios: {summary['total_repositories']}")
lines.append(f"- Repositórios privados: {summary['private_repositories']}")
lines.append(f"- Repositórios públicos: {summary['public_repositories']}")
lines.append(f"- Permissões do token: `{json.dumps(summary['token_permissions'], ensure_ascii=False)}`")
lines.append("")
lines.append("## Repositórios")
lines.append("")

for i, r in enumerate(report["repositories"], 1):
    lines.append(f"### {i}. {r['full_name']}")
    lines.append(f"- URL: {r['html_url']}")
    lines.append(f"- Owner: {r['owner']}")
    lines.append(f"- Visibility: {r['visibility']}")
    lines.append(f"- Private: {r['private']}")
    lines.append(f"- Default branch: {r['default_branch']}")
    lines.append(f"- Archived: {r['archived']}")
    lines.append(f"- Disabled: {r['disabled']}")
    lines.append(f"- Fork: {r['fork']}")
    lines.append(f"- Updated at: {r['updated_at']}")
    lines.append(f"- Pushed at: {r['pushed_at']}")
    lines.append(f"- Permissions: `{json.dumps(r['permissions'], ensure_ascii=False)}`")
    lines.append("")

with open("github-app-access-report.md", "w", encoding="utf-8") as f:
    f.write("
".join(lines))
