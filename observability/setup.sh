#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

ENV_FILE="${ENV_FILE:-../elastic-start-local/.env}"
set -a
. "$ENV_FILE"
set +a

ES_URL="${ES_URL:-http://localhost:9200}"
KIBANA_URL="${KIBANA_URL:-http://localhost:5601}"
AUTH="Authorization: ApiKey ${ES_LOCAL_API_KEY:?ES_LOCAL_API_KEY is not set}"
ALERT_RULE_ID=086e64fc-4143-48bc-a52a-f03963ea1b78

es_put() {
  echo "PUT $1"
  curl -sSf -X PUT -H "$AUTH" -H 'Content-Type: application/json' "$ES_URL/$1" --data-binary "@$2"
  echo
}

for file in elasticsearch/ilm/*.json; do
  es_put "_ilm/policy/$(basename "$file" .json)" "$file"
done

for file in elasticsearch/index-templates/*.json; do
  es_put "_index_template/$(basename "$file" .json)" "$file"
done

echo "POST saved objects import"
curl -sSf -X POST -H "$AUTH" -H 'kbn-xsrf: true' \
  "$KIBANA_URL/api/saved_objects/_import?overwrite=true" \
  -F "file=@kibana/saved-objects.ndjson"
echo

echo "POST enable alert rule $ALERT_RULE_ID"
curl -sSf -X POST -H "$AUTH" -H 'kbn-xsrf: true' \
  "$KIBANA_URL/api/alerting/rule/$ALERT_RULE_ID/_enable"
echo
