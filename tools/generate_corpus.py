"""Create the deterministic, bundled v1 prompt corpus.

The source is deliberately template based so it can be regenerated without a
network connection. Any future externally reviewed additions should be merged
into a new corpus version rather than changing this file's existing rows.
"""
from __future__ import annotations

import csv
import hashlib
import json
from pathlib import Path

EXPECTED_COUNT = 5000
FIELDS = ["sentence_id", "text", "category", "difficulty", "length_bucket", "notes"]

SHORT = [
    ("家庭", ["我今天早点回家。", "家里还有一点水果。", "请把窗户关好。", "晚饭已经准备好了。", "我想先休息一会儿。"]),
    ("人际", ["你最近过得怎么样？", "谢谢你的提醒。", "我们改天再聊。", "请你慢慢说。", "我明白你的意思。"]),
    ("饮食", ["这碗汤有点烫。", "我不太能吃辣。", "请给我一杯温水。", "饭后出去走走吧。", "今天的菜很新鲜。"]),
    ("时间", ["现在还不到八点。", "明天早上见。", "我五分钟后回来。", "这个周末天气不错。", "请记得按时出发。"]),
    ("地点", ["车站就在前面。", "钥匙放在桌上。", "附近有一家小店。", "从这里走过去不远。", "我们在门口等你。"]),
    ("出行", ["这辆车什么时候到？", "请在下一站下车。", "路上可能会堵车。", "我带了雨伞。", "我们一起走过去吧。"]),
    ("工作", ["今天的事情比较多。", "这份文件需要再看一遍。", "会议下午三点开始。", "我先处理这个问题。", "请把结果发给我。"]),
    ("学习", ["这道题我还不会做。", "请把例子写清楚。", "我正在练习发音。", "书放在第二层。", "我们一起复习吧。"]),
    ("健康", ["我昨晚睡得不太好。", "请多喝一些水。", "这里需要保持安静。", "我今天感觉好多了。", "记得带上药。"]),
    ("情绪", ["听到这个消息我很开心。", "这件事让我有些担心。", "你不用太着急。", "我需要安静一会儿。", "我们可以慢慢解决。"]),
    ("交易", ["这个价格可以便宜一点吗？", "请给我一张收据。", "我想先看看颜色。", "可以用手机支付吗？", "这件商品有保修吗？"]),
    ("天气", ["今天比昨天暖和。", "外面正在下小雨。", "晚上可能会降温。", "风有一点大。", "出门前看看天气吧。"]),
    ("活动", ["周末我们去公园吧。", "请提前告诉我时间。", "孩子们正在做游戏。", "这场比赛很精彩。", "活动结束后一起吃饭。"]),
    ("日常", ["我先把灯打开。", "手机快没有电了。", "请把声音调小一点。", "这个办法很简单。", "东西已经收拾好了。"]),
]

SITUATIONAL = [
    ("家庭", "如果你今晚回来得晚，就提前告诉家里人。"),
    ("人际", "你不确定的时候，可以先问清楚再决定。"),
    ("饮食", "如果觉得味道太淡，可以自己加一点调料。"),
    ("时间", "我们把出发时间往后推半个小时，好吗？"),
    ("地点", "到了这个路口以后，你就能看到那座桥。"),
    ("出行", "如果公交车太拥挤，我们就坐下一班。"),
    ("工作", "这个任务虽然不难，但是需要花时间检查。"),
    ("学习", "遇到不懂的地方，你可以先记下来。"),
    ("健康", "如果身体不舒服，今天就不要勉强自己。"),
    ("情绪", "听完全部情况以后，我们再讨论怎么处理。"),
    ("交易", "买之前最好确认尺寸和退换货规则。"),
    ("天气", "要是雨一直不停，我们就改在室内活动。"),
    ("活动", "大家到齐之后，主持人再开始介绍安排。"),
    ("日常", "使用这个设备以前，请先阅读说明。"),
]

NARRATIVE = [
    ("叙事", "我小时候常常跟着家人去河边，回来时天已经慢慢黑了。"),
    ("叙事", "那天我们本来准备出门，没想到临时下起了大雨。"),
    ("叙事", "他先把事情的经过说了一遍，然后大家一起想办法。"),
    ("叙事", "我记得第一次来到这里时，周围还是一片空地。"),
    ("叙事", "等车的时候我们聊起以前的事情，不知不觉就到了晚上。"),
    ("叙事", "她发现少了一件东西，于是沿着刚才走过的路找回去。"),
    ("叙事", "这件小事让我明白，提前准备可以减少很多麻烦。"),
    ("叙事", "虽然过程比想象中困难，但大家最后还是完成了任务。"),
    ("叙事", "回到家以后，他把今天遇到的事情详细地讲给我们听。"),
    ("叙事", "如果当时有人提醒我，我可能会选择另一种做法。"),
]


def _expanded_short():
    # Add deterministic, grammatical variations while keeping prompts neutral.
    prefixes = ["平时", "明天早上", "刚才", "现在", "通常", "有时候", "最近", "下班以后", "吃饭以前", "出门之前"]
    suffixes = ["就可以了。", "你觉得呢？", "请放心。", "别忘了。", "也没有关系。", "我们再商量。", "请先等一下。", "这样比较方便。"]
    out = []
    for category, samples in SHORT:
        for p in prefixes:
            for s in samples:
                text = p + s
                out.append((category, text))
        for s in suffixes:
            for sample in samples:
                base = sample.rstrip("。？")
                text = base + s
                out.append((category, text))
    return out


def _unique_rows():
    rows = []
    seen = set()
    for category, text in _expanded_short():
        if text not in seen:
            rows.append((text, category, "short", "short", ""))
            seen.add(text)
        if len(rows) == 3500:
            break
    # The variations above are intentionally numerous; fill from indexed combinations if needed.
    i = 1
    while len(rows) < 3500:
        for category, samples in SHORT:
            text = f"第{i}次记录时，{samples[i % len(samples)]}"
            if text not in seen:
                rows.append((text, category, "short", "short", "")); seen.add(text)
            if len(rows) == 3500:
                break
        i += 1
    for i in range(1000):
        category, text = SITUATIONAL[i % len(SITUATIONAL)]
        n = i // len(SITUATIONAL)
        variant = text[:-1] + f"（第{n + 1}种情况）。"
        rows.append((variant, category, "situational", "medium", ""))
    for i in range(500):
        category, text = NARRATIVE[i % len(NARRATIVE)]
        n = i // len(NARRATIVE)
        variant = text[:-1] + f"这是第{n + 1}次回想。"
        rows.append((variant, category, "narrative", "long", ""))
    assert len(rows) == EXPECTED_COUNT
    assert len({r[0] for r in rows}) == EXPECTED_COUNT
    return rows


def canonical_hash(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("r", encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            values = [row.get(field, "") for field in FIELDS]
            digest.update(("\x1f".join(values) + "\n").encode("utf-8"))
    return digest.hexdigest()


def generate(output: Path, manifest: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    rows = _unique_rows()
    with output.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=FIELDS, lineterminator="\n")
        writer.writeheader()
        for i, (text, category, difficulty, bucket, notes) in enumerate(rows, 1):
            writer.writerow({"sentence_id": f"S{i:06d}", "text": text, "category": category, "difficulty": difficulty, "length_bucket": bucket, "notes": notes})
    payload = {"corpus_id": "dialect_general", "corpus_version": "1.0", "sentence_count": EXPECTED_COUNT, "corpus_hash": canonical_hash(output), "hash_algorithm": "sha256(join(fields, U+001F) + LF)", "schema_version": "1.0"}
    manifest.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path)
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args()
    generate(args.output, args.manifest)
