#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
时间戳自动注入脚本 v2
按洛卡要求修改：
- 每隔1小时在最新消息中注入一次时间（仅时:分）
- 跨过0点时，在0点后的第一条消息中注入完整年月日
- 注入内容仅AI可见，不显示在聊天气泡中
"""

import datetime
import json
import os

STATE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".timestamp_state.json")

WEEKDAY_MAP = {
    0: "星期一", 1: "星期二", 2: "星期三",
    3: "星期四", 4: "星期五", 5: "星期六", 6: "星期日"
}


def load_state():
    if os.path.exists(STATE_FILE):
        with open(STATE_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    return {"last_hour": -1, "last_date": ""}


def save_state(hour, date_str):
    with open(STATE_FILE, "w", encoding="utf-8") as f:
        json.dump({"last_hour": hour, "last_date": date_str}, f)


def inject(now=None):
    """
    返回值:
      - 需要注入时返回注入字符串
      - 不需要注入时返回 None
    """
    if now is None:
        now = datetime.datetime.now()

    state = load_state()
    cur_date = now.strftime("%Y-%m-%d")
    cur_hour = now.hour
    weekday = WEEKDAY_MAP[now.weekday()]

    crossed_day = (state["last_date"] != cur_date)
    crossed_hour = (state["last_hour"] != cur_hour)

    if not (crossed_day or crossed_hour):
        return None  # 同一小时内不重复注入

    # 跨日（含首次运行）→ 注入完整年月日+时分
    if crossed_day:
        tag = (
            f"<time_reminder>系统注入·仅AI可见·历史时间锚点 | "
            f"{cur_date} {now.strftime('%H:%M')} ({weekday}) | "
            f"24小时制 | 不显示在聊天气泡</time_reminder>"
        )
    else:
        # 同日跨小时 → 仅注入时分
        tag = (
            f"<time_reminder>系统注入·仅AI可见·历史时间锚点 | "
            f"{now.strftime('%H:%M')} | "
            f"24小时制 | 不显示在聊天气泡</time_reminder>"
        )

    save_state(cur_hour, cur_date)
    return tag


if __name__ == "__main__":
    result = inject()
    if result:
        print(result)
