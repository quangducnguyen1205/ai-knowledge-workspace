# Project3 → Thesis Handoff

## What Project3 is, for research purposes

Project3 is a **working engineering artifact**, not a scientific contribution. Nothing in it has
been evaluated against a baseline, no hypothesis has been tested, and no result is publishable as
it stands. What it provides is an *instrumented substrate*: a system where the retrieval path,
the canonical transcript, the moment-level interaction events and the failure behaviour are all
explicit, isolated and measurable.

That is worth something for a thesis, and it is worth exactly that much.

**Fork, do not rename.** A thesis should start from a fork of Project3 and evolve it under its own
name. Project3 remains the closed, citable baseline; renaming it would destroy the very thing that
makes it useful — a fixed, reproducible point of comparison. The fork's first commit should record
the Project3 commit hash it branched from.

## Why this codebase supports experiments better than a fresh prototype

| Property already in place | Why it matters for an experiment |
|---|---|
| PostgreSQL is truth; Elasticsearch is rebuildable derived state | The retrieval layer can be swapped or reindexed without touching product truth — a treatment condition is a reindex, not a migration |
| Canonical transcript rows with stable identity and timing | Ground truth can be annotated once at row granularity and reused across every condition |
| `POST /api/assets/{id}/index` rebuilds the whole index | Deterministic, repeatable condition setup |
| Search policy is isolated (`search/application/`) | The independent variable can be changed in one module without touching authorization, persistence or UI |
| Saved moments and playback progress are already persisted per user | Behavioural dependent variables exist without adding new instrumentation |
| Bounded, documented failure modes | A degraded condition can be induced deliberately and safely |
| Flyway `V1`–`V7` immutable chain, `ddl-auto=validate` | Schema state of any experiment run is reconstructible |

## Direction 1 — Canonical-truth validation as a retrieval-quality mechanism

**Research question.** Does validating each retrieved candidate against the system of record before
returning it measurably improve end-user retrieval correctness under realistic index staleness,
compared with returning index results directly?

**Hypothesis.** Under a non-zero index-staleness rate, per-hit canonical validation reduces the rate
of *incorrect-position* results (a result whose timestamp no longer corresponds to its text) more
than it reduces recall, yielding net higher task success.

**Independent variables.**
- Validation mode: none / identity-only / full field comparison (the current implementation).
- Staleness rate: fraction of indexed rows whose canonical row has changed since indexing (0 %, 5 %,
  15 %, 30 %).
- Staleness kind: text edit, re-segmentation, timing shift, row deletion.

**Dependent metrics.** Precision@k and nDCG@10 computed against the *canonical* transcript, not the
index; incorrect-position rate; result-count instability; p50/p95 search latency; buffers read per
search.

**Baseline.** Validation disabled — the standard "index result is the answer" architecture. This is
the honest comparator, and Project3's current behaviour is the treatment, not the baseline.

**Corpus strategy.** Extend the existing Phase 7 versioned lexical evaluation corpus with a
staleness generator: apply a controlled mutation to a sampled fraction of canonical rows *without*
reindexing. Because the transcript is the truth, the post-mutation ground truth is derivable
automatically — no re-annotation per condition.

**Experiment design.** Full factorial 3 × 4 × 4, each cell replicated over a fixed query set with a
fixed seed. Rebuild the index from a clean state before each cell. Report per-query paired
differences, not only cell means.

**Threats to validity.** Synthetic staleness may not match how real transcripts drift — mitigate by
including at least one real re-transcription of the same media as an observational condition. The
corpus is small and lexical, so effects may not transfer to semantic retrieval. Latency measured on
a single machine confounds with local load; use buffer counts alongside wall-clock time.

**Required fork changes.** A configuration switch for validation mode (currently unconditional); a
staleness-injection harness; an offline evaluation runner that scores against canonical rows; per-hit
decision logging (`kept` / `dropped` / reason) behind an experiment flag.

## Direction 2 — Row-granular versus window-granular retrieval for spoken content

**Research question.** What retrieval granularity over time-aligned speech best supports "find the
moment", and does the optimum depend on query type?

**Hypothesis.** Fixed transcript-row granularity (the current design) is not optimal for
conceptual queries; overlapping multi-row windows improve moment-level recall for conceptual queries
without materially harming precision for verbatim queries.

**Independent variables.** Indexing unit: single row / N-row sliding window (N ∈ {2, 3, 5}) with
stride s / speaker- or pause-bounded segment. Query type: verbatim phrase / paraphrase / conceptual.

**Dependent metrics.** Moment-level recall within a ±t-second tolerance (t ∈ {5, 15, 30}); mean
absolute offset between the returned start time and the annotated ground-truth start; index size;
indexing time; search latency.

**Baseline.** The current one-document-per-row index, unchanged.

**Corpus strategy.** 15–25 lecture-style recordings with human-annotated *answer intervals* (start,
end) rather than answer rows — interval annotation survives every granularity change, so it is
annotated once and reused. Include Vietnamese and English material; record accent handling
explicitly, since the current system has a documented gap on unaccented Vietnamese queries.

**Experiment design.** Within-subjects over queries: every query runs against every indexing
condition built from the identical canonical transcript. Tolerance-based scoring makes conditions
with different unit sizes directly comparable. Pre-register the tolerance values.

**Threats to validity.** Annotator disagreement on interval boundaries — measure inter-annotator
agreement on a shared subset. Overlapping windows inflate the candidate pool and interact with the
result-diversification policy; hold diversification fixed or treat it as a nuisance factor. Elasticsearch
scoring is not granularity-neutral; report raw scores alongside ranks.

**Required fork changes.** An indexing-unit strategy behind the existing indexing port; a mapping
variant per unit; an interval-based evaluation harness; corpus and annotation storage outside the
product schema.

## Direction 3 — Do stable moment addresses change how people use recorded lectures?

**Research question.** Does providing stable, shareable, row-addressed moment links (with saved
moments and resume) change re-access behaviour and perceived task efficiency, compared with
conventional timestamped video links?

**Hypothesis.** Row-addressed moments increase repeat access to previously visited moments and
reduce time-to-relocate a known passage, relative to second-offset links, with the difference growing
when the transcript has been regenerated between sessions.

**Independent variables.** Addressing mode: second-offset URL / row-addressed URL (current) /
row-addressed plus saved-moment list. Session gap: same session / after re-transcription.

**Dependent metrics.** Time-to-relocate a known passage; number of navigation actions per
relocation; saved-moment reuse rate; Continue-watching resume accuracy; a short standard
self-report instrument for perceived effort.

**Baseline.** Second-offset links — what YouTube and every conventional player provide.

**Corpus and participants.** A fixed course-like corpus of 6–10 recordings and a scripted set of
relocation tasks. 20–30 participants is a realistic student-thesis scale; report it as such and do
not over-claim generalization.

**Experiment design.** Within-subjects, counterbalanced task order, with a practice block. Instrument
by reading the data the system already persists (saved moments, playback progress) plus a bounded
interaction log added in the fork. Ethics review and informed consent are required before any
participant data is collected.

**Threats to validity.** Learning effects across conditions — counterbalance and use distinct tasks
per condition. The interface is not a controlled variable, so a UI difference may explain an effect
attributed to addressing; keep every non-addressing element identical. Self-selected participants
(likely fellow students) limit external validity. Small N means the study should be powered and
reported as exploratory.

**Required fork changes.** A second-offset addressing mode for the control condition; a
condition-assignment mechanism; a bounded, consented interaction log kept **outside** the product
schema; a task harness. Participant data must not be committed to the repository.

## Direction 4 — Grounded assistant answers over canonical transcript context

**Research question.** How does the amount and selection of canonical transcript context supplied to
a local language model affect the *citation faithfulness* of generated answers over lecture content?

**Hypothesis.** Faithfulness rises with retrieved context up to a saturation point and then declines
as distractor rows dilute attention; row-level citation requirements dampen the decline.

**Independent variables.** Context size (top-k rows, k ∈ {3, 5, 10, 20}); context selection
(top-scored rows / score plus temporal neighbourhood / diversified across Assets); citation
requirement (free-form answer / mandatory row citations, the current design).

**Dependent metrics.** Citation faithfulness — does each cited row actually support the claim, human-
or LLM-judged with a human-validated subsample; unsupported-claim rate; answer relevance;
end-to-end latency; refusal rate when context is insufficient.

**Baseline.** A fixed small-k, top-scored, free-form configuration.

**Corpus strategy.** 100–200 questions over the annotated recordings from Direction 2, each with the
supporting interval(s) marked. Reuse the same annotations — this is why Direction 2 should run first
if both are pursued.

**Experiment design.** Factorial over k × selection × citation requirement, one fixed local model and
fixed decoding parameters, several seeds per cell. Judge with a rubric applied blind to condition;
validate the automated judge against human ratings on a stratified subsample before trusting it.

**Threats to validity.** LLM-as-judge bias, especially if the judge shares a family with the
generator — use a different model and report human agreement. Local model non-determinism — fix
seeds and report variance. Results are model-specific and should not be generalized across model
families. Latency on a laptop-hosted model is not a meaningful systems result.

**Required fork changes.** Parameterized retrieval-for-assistant configuration; deterministic
decoding settings; an evaluation harness with blind judging; a question/answer annotation store
outside the product schema.

## Practical sequencing

Directions 1 and 2 are the strongest fit: the substrate already exists, the ground truth is derivable
from the canonical transcript, and neither needs human participants. Direction 2 produces the
annotated corpus that Direction 4 depends on. Direction 3 is the highest-effort option because of
participant recruitment and ethics approval, and should only be chosen if a human-computer
interaction supervisor is involved.

## Rules for the fork

1. Branch from a named Project3 commit and record that hash in the fork's first commit message.
2. Keep experiment code, corpora and participant data out of the product schema and out of the
   product modules.
3. Treat the Project3 behaviour as a **condition**, not as the default — every experiment needs a
   configuration that reproduces it exactly and one that does not.
4. Do not modify `V1`–`V7`. Experiment schema is additive, in the fork's own migration range.
5. Report Project3 as prior engineering work and a baseline system. It is not a result.
