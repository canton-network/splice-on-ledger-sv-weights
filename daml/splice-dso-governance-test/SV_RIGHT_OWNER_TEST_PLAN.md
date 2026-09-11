# SV right owners — Daml test plan

## Model invariants

- After every flow the right-owner names in `DsoRules`, the live `SvRightOwner` contracts and the
  reward states are the same set, and it is never empty — so the last owner cannot be removed.
- Right-owner names are unique while live and share one namespace with SV node operator names, and a
  choice rejects any contract handed to it under the wrong name.
- An owner's weight, beneficiary ratios and hosting node operator are checked when the vote is raised
  and again against the result when it executes.
- Only migration checks the network's total reward weight; adding, removing and updating owners move
  it freely, and none of them changes a node operator's own weight.

## Adding, changing and removing right owners

- Owners are added, changed and removed only by a vote, which records an instruction that any SV node
  operator then executes against the current owner contract.
- A governed update can rewrite every field of an owner, its party included.
- Removing an owner archives its reward state and leaves its co-tenants untouched; the freed name can
  later be added again as a new owner, collecting from the round the add lands in.
- Every instruction expires, only an SV node operator can expire it, and an expired instruction cannot
  be executed.
- Two instructions for the same owner resolve to one outcome, the loser can only expire, and a passed
  vote whose action can no longer succeed still closes.
- Owners hosted by the same node operator are governed independently: two updates that take effect at
  the same time both land.
- The same actions taken through the confirmation route need the same SV threshold as the vote route.

## Beneficiaries and reward splitting

- Beneficiaries never take more than the owner's weight; each share is exact, rounding included, and
  the remainder is minted to the owner in the same round.
- A right owner sets its own beneficiaries; its hosting node operator, another owner and an outside
  party cannot.
- A right owner that is not itself an SV node operator can still set them.
- The owner's own update replaces the whole set, while a governed update merges beneficiary by
  beneficiary, so a weight vote leaves the owner's edits alone.

## Reward minting

- Only the hosting node operator mints for an owner — not another operator, not the owner itself — and
  it can mint for one or many of its owners in a single transaction.
- A round can be minted from the moment it opens until its contract is archived, once per owner, and
  never once a later round has been collected.
- The reward-state counters for rounds collected, rounds missed and coupons issued track what actually
  happened.
- The coupons produced are claimable and issue the expected Amulet.
- One owner's configuration cannot stop its co-tenants minting in the same batch.
- No other choice moves a right owner's reward state, `DsoRules_MergeSvRewardState` included.

## Migration and the SV node set

- Migration maps the legacy node operator weights onto right owners, one-to-one or not, and rejects a
  mapping that is incomplete or changes the total weight.
- Reward state carries over by name; the state of an operator that does not continue is archived, and
  none is left orphaned.
- No owner loses or repeats a round across the cut, except the single round skipped for a name with no
  legacy predecessor.
- Before migration every legacy flow works and every right-owner choice is refused; afterwards the
  reverse.
- A network migrates once: no second migration and no way back.
- A network that is on-ledger from genesis behaves exactly like a migrated one.
- An SV node can still be onboarded after migration, and an onboarding that still carries a reward
  weight is rejected at confirmation rather than left to expire.
- Offboarding a node operator leaves none of its right owners unable to mint and no reward state
  orphaned; nothing repairs the link by itself.
- Hosting is by operator name: an owner can be moved to another operator by vote, and re-onboarding an
  offboarded name re-hosts its owners to the new party.
