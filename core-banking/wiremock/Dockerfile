# Packages the same debit-eligibility stub used for local dev (see mappings/) as a
# standalone image, so it can be deployed as its own ECS Fargate service in AWS -
# same tool, same mappings, same alice/bob/carol behavior in both places.
FROM wiremock/wiremock:3.4.2

COPY mappings /home/wiremock/mappings
