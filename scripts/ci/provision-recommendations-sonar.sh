#!/bin/sh
set -eu
: "${SONAR_TOKEN:?Missing Sonar token}"
: "${SONAR_ORGANIZATION:?Missing Sonar organization}"
: "${SONAR_API_PROJECT_KEY:?Missing API project key}"
: "${SONAR_PROJECT_KEY:?Missing recommendations project key}"

sonar_get() {
  endpoint=$1
  shift
  curl --proto '=https' --fail --silent --show-error --get \
    -H "Authorization: Bearer $SONAR_TOKEN" \
    "https://sonarcloud.io/api/$endpoint" --data-urlencode "organization=$SONAR_ORGANIZATION" "$@"
}
sonar_post() {
  endpoint=$1
  shift
  curl --proto '=https' --fail --silent --show-error --request POST \
    -H "Authorization: Bearer $SONAR_TOKEN" \
    "https://sonarcloud.io/api/$endpoint" --data-urlencode "organization=$SONAR_ORGANIZATION" "$@"
}
projects=$(sonar_get projects/search --data-urlencode "projects=$SONAR_PROJECT_KEY")
if [ "$(printf '%s' "$projects" | jq '.components | length')" -eq 0 ]; then
  sonar_post projects/create --data-urlencode "project=$SONAR_PROJECT_KEY" \
    --data-urlencode 'name=rent-cars-recommendations' --data-urlencode visibility=public >/dev/null
fi
# Reuse the API policy; do not request admin mutations when it already matches.
gate=$(sonar_get qualitygates/get_by_project --data-urlencode "project=$SONAR_API_PROJECT_KEY" | jq -er '.qualityGate.id')
current_gate=$(sonar_get qualitygates/get_by_project --data-urlencode "project=$SONAR_PROJECT_KEY" | jq -er '.qualityGate.id')
if [ "$gate" != "$current_gate" ]; then
  sonar_post qualitygates/select --data-urlencode "projectKey=$SONAR_PROJECT_KEY" --data-urlencode "gateId=$gate" >/dev/null
fi
profiles=$(sonar_get qualityprofiles/search --data-urlencode "project=$SONAR_API_PROJECT_KEY")
current_profiles=$(sonar_get qualityprofiles/search --data-urlencode "project=$SONAR_PROJECT_KEY")
profile_keys=$(printf '%s' "$profiles" | jq -er '.profiles[].key')
printf '%s\n' "$profile_keys" | while IFS= read -r profile; do
  if ! printf '%s' "$current_profiles" | jq -e --arg key "$profile" '.profiles | any(.key == $key)' >/dev/null; then
    sonar_post qualityprofiles/add_project --data-urlencode "project=$SONAR_PROJECT_KEY" --data-urlencode "key=$profile" >/dev/null
  fi
done
