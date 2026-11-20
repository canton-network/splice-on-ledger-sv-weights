#!/usr/bin/env bash
# approved-sv-id-values.DevNet.yaml -> Convert.daml (production types and checks) -> sv-right-owners.DevNet.json.
# Run from a direnv shell after `sbt damlBuild`.
set -euo pipefail
cd "$(dirname "$0")"
root=$(git rev-parse --show-toplevel)
build=$(mktemp -d)
trap 'rm -rf "$build"' EXIT
cp Convert.daml "$build/"
cat > "$build/daml.yaml" <<EOF
sdk-version: 3.5.2
name: sv-rights-owners-conversion
source: Convert.daml
version: 0.0.1
dependencies: [daml-prim, daml-stdlib, daml-script]
data-dependencies:
  - $root/daml/splice-util/.daml/dist/splice-util-current.dar
  - $root/daml/splice-amulet/.daml/dist/splice-amulet-current.dar
build-options: [--target=2.3]
EOF
# The yaml has no SV and DSO party ids: `<name>::1220` followed by 64 zeros stands for them.
# A beneficiary comment is split into `label` (up to the second `#`) and `role` (the rest).
yq -o=json '{
  "dso": "DSO::12200000000000000000000000000000000000000000000000000000000000000000",
  "approvedSvIdentities": [.approvedSvIdentities[] | {
    "name": .name,
    "svParty": (.name + "::12200000000000000000000000000000000000000000000000000000000000000000"),
    "svRightsOwnerName": .svRightsOwnerName,
    "rewardWeightBps": .rewardWeightBps,
    "extraBeneficiaries": [(.extraBeneficiaries // [])[] | {
      "beneficiary": .beneficiary,
      "svRightsOwnerName": .svRightsOwnerName,
      "weight": .weight,
      "label": (.beneficiary | line_comment | split("#") | .[0] | trim),
      "role": (.beneficiary | line_comment | split("#") | .[1:] | join("#") | trim)
    }]
  }]
}' approved-sv-id-values.DevNet.yaml > "$build/input.json"
(cd "$build" && dpm build -o convert.dar && dpm script --ide-ledger --dar convert.dar --script-name Convert:main --input-file input.json --output-file output.json)
jq 'to_entries | sort_by(.key) | from_entries
  | map_values(.svNodeAssignments |= map_values({svNodeName, rewardWeight, escrowBeneficiaries, beneficiaries, defaultBeneficiary}))' \
  "$build/output.json" > sv-right-owners.DevNet.json
