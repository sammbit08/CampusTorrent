#!/usr/bin/env python3
"""Plots results/experiments.csv (written by ExperimentRunner) as bar charts with std-dev error bars.

Usage:  python3 scripts/plot_results.py --csv results/experiments.csv --out results/plots
Needs:  pip install matplotlib
Only the numbers in the CSV are plotted; nothing is invented or smoothed.
Method: per run, take the MEAN download time of the contributing leechers (free-riders reported separately);
then plot the mean and sample standard deviation of those per-run values across runs.
"""
import argparse
import csv
import os
import statistics
from collections import OrderedDict, defaultdict

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt


def load(path):
    with open(path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def mean_std(values):
    if not values:
        return 0.0, 0.0
    return statistics.mean(values), (statistics.stdev(values) if len(values) > 1 else 0.0)


def per_run_means(rows, experiment, want_free_riders=False):
    """config -> list of per-run mean durations (seconds) of finished, verified leechers."""
    runs = defaultdict(lambda: defaultdict(list))
    for r in rows:
        if r["experiment"] != experiment or r["role"] != "leecher":
            continue
        if r["completed"] != "true" or r["verified"] != "true":
            continue
        if (r["is_free_rider"] == "true") != want_free_riders:
            continue
        runs[r["config"]][int(r["run"])].append(int(r["duration_ms"]) / 1000.0)
    out = OrderedDict()
    for cfg, by_run in runs.items():
        out[cfg] = [statistics.mean(v) for _, v in sorted(by_run.items())]
    return out


def bar(ax, labels, series, title, ylabel):
    """series: list of (legend, [per-config list of per-run values])."""
    width = 0.8 / len(series)
    for i, (legend, per_cfg) in enumerate(series):
        xs = [j + i * width for j in range(len(labels))]
        ms = [mean_std(v) for v in per_cfg]
        ax.bar(xs, [m for m, _ in ms], width, yerr=[s for _, s in ms], capsize=4, label=legend)
        for x, (m, _), v in zip(xs, ms, per_cfg):
            if v:
                ax.text(x, m, f"n={len(v)}", ha="center", va="bottom", fontsize=7)
    ax.set_xticks([j + width * (len(series) - 1) / 2 for j in range(len(labels))])
    ax.set_xticklabels(labels, rotation=15)
    ax.set_ylabel(ylabel)
    ax.set_title(title)
    if len(series) > 1:
        ax.legend()


def plot_simple(rows, experiment, title, outdir):
    data = per_run_means(rows, experiment)
    if not data:
        print(f"no data for experiment '{experiment}', skipping")
        return
    fig, ax = plt.subplots(figsize=(7, 4))
    bar(ax, list(data.keys()), [("contributing leechers", list(data.values()))], title, "mean download time (s)")
    fig.tight_layout()
    path = os.path.join(outdir, f"{experiment}.png")
    fig.savefig(path, dpi=150)
    plt.close(fig)
    print("wrote", path)


def plot_freerider(rows, outdir):
    contrib = per_run_means(rows, "freerider", False)
    riders = per_run_means(rows, "freerider", True)
    if not contrib:
        print("no data for experiment 'freerider', skipping")
        return
    labels = list(contrib.keys())
    fig, ax = plt.subplots(figsize=(8, 4))
    bar(ax, labels,
        [("contributing leechers", [contrib.get(l, []) for l in labels]),
         ("free-rider", [riders.get(l, []) for l in labels])],
        "Free-rider vs contributors (choking on/off)", "mean download time (s)")
    fig.tight_layout()
    path = os.path.join(outdir, "freerider.png")
    fig.savefig(path, dpi=150)
    plt.close(fig)
    print("wrote", path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--csv", default="results/experiments.csv")
    ap.add_argument("--out", default="results/plots")
    a = ap.parse_args()
    rows = load(a.csv)
    os.makedirs(a.out, exist_ok=True)
    plot_simple(rows, "strategy", "Piece selection strategy (1 seeder)", a.out)
    plot_simple(rows, "seeders", "Number of seeders (rarest-first)", a.out)
    plot_freerider(rows, a.out)

    # also print the table that backs the plots
    print("\nconfig, runs, mean_s, std_s")
    for exp in ("strategy", "seeders"):
        for cfg, v in per_run_means(rows, exp).items():
            m, s = mean_std(v)
            print(f"{cfg}, {len(v)}, {m:.2f}, {s:.2f}")
    for label, fr in (("contributors", False), ("free-rider", True)):
        for cfg, v in per_run_means(rows, "freerider", fr).items():
            m, s = mean_std(v)
            print(f"{cfg} [{label}], {len(v)}, {m:.2f}, {s:.2f}")


if __name__ == "__main__":
    main()
