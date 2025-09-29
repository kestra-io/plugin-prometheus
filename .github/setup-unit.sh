docker compose -f docker-compose-ci.yml up -d

until curl -s http://localhost:9091/metrics > /dev/null; do
  sleep 2
done

until curl -s http://localhost:9090/-/ready > /dev/null; do
  sleep 2
done

# we push metric for trigger test
echo 'kestra_trigger_test_metric{job="trigger_test"} 150' \
  | curl --data-binary @- http://localhost:9091/metrics/job/trigger_test/instance/local

