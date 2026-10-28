#!/usr/bin/env python3
"""Count the frozen migration work list; optionally render its human-readable view.

This reports recorded scope and evidence. It does not run tests, inspect build
artifacts, infer correctness from file presence, or mark any item complete.
"""
import argparse
from collections import Counter
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs/MIGRATION-CHECKLIST.json"
OUTPUT = ROOT / "docs/MIGRATION-CHECKLIST.md"
LABELS = {"complete": "已闭合", "review": "已有实现，待核对/验收", "gap": "有明确缺口", "skipped": "按用户要求跳过"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="regenerate the Markdown view")
    parser.add_argument("--remaining", action="store_true", help="also list every open ID")
    args = parser.parse_args()
    data = json.loads(SOURCE.read_text(encoding="utf-8"))
    baseline, items = data["baseline"], data["items"]
    ids = [item["id"] for item in items]
    if len(ids) != baseline["locked_total"] or len(set(ids)) != len(ids):
        raise SystemExit("Checklist IDs must be unique and match the explicitly frozen total")
    if any(item["status"] not in LABELS for item in items):
        raise SystemExit("Unknown checklist status")
    if any(not item["acceptance"] or (not item["evidence"] if item["status"] == "complete"
                                    else not item["remaining"]) for item in items):
        raise SystemExit("Every item needs closure criteria and either evidence or an open reason")
    counts = Counter(item["status"] for item in items)
    total = len(items)
    remaining = total - counts["complete"] - counts["skipped"]
    print(f"total={total} complete={counts['complete']} skipped={counts['skipped']} remaining={remaining} "
          f"review={counts['review']} gap={counts['gap']}")
    groups = list(dict.fromkeys(item["group"] for item in items))
    for group in groups:
        subset = [item for item in items if item["group"] == group]
        done = sum(item["status"] == "complete" for item in subset)
        skipped = sum(item["status"] == "skipped" for item in subset)
        print(f"{group}: total={len(subset)} complete={done} skipped={skipped} remaining={len(subset) - done - skipped}")
    if args.remaining:
        for item in items:
            if item["status"] not in ("complete", "skipped"):
                print(f"{item['id']} {item['name']}: {item['remaining']}")
    if args.write:
        render(baseline, items, groups, counts, remaining)


def render(baseline, items, groups, counts, remaining):
    lines = ["# 完整移植固定清单", "",
             "此文件由 `python tools/migration_status.py --write` 生成；",
             "唯一状态源为 [MIGRATION-CHECKLIST.json](MIGRATION-CHECKLIST.json)。", "",
             f"原版基线：[`{baseline['upstream_commit']}`]({baseline['upstream_url']})。", "",
             f"**总计 {len(items)} 项，已闭合 {counts['complete']} 项，按用户要求跳过 {counts['skipped']} 项，剩余 {remaining} 项。**", "",
             f"剩余包括：已有实现待核对/验收 {counts['review']} 项，有明确缺口 {counts['gap']} 项。", "",
             "这是固定工作项数量，不是工时比例；已有实现待验收不等于尚未编写该功能。", "",
             "## 计数与结束条件", "", baseline["counting_rule"], "",
             baseline["scope_change_rule"], "", baseline["closure_rule"], "",
             "跨模块的模型、菜单、多人等工作由对应 C 项统一负责，不能用某个设备的局部运行通过替代这些项目。", "",
             "已闭合项若出现回归，重开原 ID；不添加新的总项目。状态计数不替代证据。", "",
             "## 数量", "", "| 分组 | 总数 | 已闭合 | 跳过 | 剩余 |", "| --- | ---: | ---: | ---: | ---: |"]
    for group in groups:
        subset = [item for item in items if item["group"] == group]
        done = sum(item["status"] == "complete" for item in subset)
        skipped = sum(item["status"] == "skipped" for item in subset)
        lines.append(f"| {group} | {len(subset)} | {done} | {skipped} | {len(subset) - done - skipped} |")
    for change in baseline.get("scope_adjustments", []):
        lines += ["", "范围调整（" + change["date"] + "）：" + change["reason"], "",
                  "跳过编号：" + "、".join(change["skipped_ids"]) + "。跳过项不计作已闭合。"]
    lines += ["", "## 范围归属", ""]
    lines += ["- " + text for text in baseline["scope_ownership"]]
    for group in groups:
        lines += ["", "## " + group, ""]
        for item in (item for item in items if item["group"] == group):
            lines += [f"### {item['id']} · {item['name']} · {LABELS[item['status']]}", ""]
            lines += ["完成条件：" + "；".join(item["acceptance"]), ""]
            if item["remaining"]:
                label = "跳过原因：" if item["status"] == "skipped" else "剩余："
                lines += [label + item["remaining"], ""]
            if item["upstream"]:
                lines += ["原版范围：" + "、".join(f"`{p}`" for p in item["upstream"]), ""]
            if item["implementation"]:
                lines += ["当前接入：" + "、".join(f"[{p}](../{p})" for p in item["implementation"]), ""]
            if item["evidence"]:
                label = "记录依据：" if item["status"] == "skipped" else "闭合依据："
                lines += [label + "、".join(f"[{p}](../{p})" for p in item["evidence"]), ""]
    OUTPUT.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
