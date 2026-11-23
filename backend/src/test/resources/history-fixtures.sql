-- Additional immutable audit records. No sequence usage; rollback or reserved-ID cleanup.
INSERT INTO business_event(id,type,service_date,order_id,courier_id,actor_id,occurred_at,created_at,reason,payload)
SELECT ('a0f8dc00-0000-4000-8000-'||lpad(to_hex(n),12,'0'))::uuid,t,'2099-11-09',
    CASE WHEN n=3 THEN 'a0f8d300-0000-4000-8000-000000000001'::uuid END,
    CASE WHEN n=6 THEN 'a0f8d200-0000-4000-8000-000000000001'::uuid END,
    CASE WHEN n=3 THEN 'a0f8d600-0000-4000-8000-000000000001'::uuid
         WHEN n=7 THEN 'a0f8d600-0000-4000-8000-000000000002'::uuid END,
    timestamptz '2099-11-09T01:00:00Z'+(20-n)*interval '1 minute',
    timestamptz '2099-11-09T10:00:00Z'+n*interval '1 minute',
    CASE WHEN n=3 THEN 'Причина_%!' WHEN n=8 THEN '' ELSE 'Сохранённая причина '||n END,
    CASE WHEN n=2 THEN '{"plan_version":"9000000000000000003","order_ids":["a0f8d300-0000-4000-8000-000000000001","a0f8d300-0000-4000-8000-000000000002"]}'::jsonb
         WHEN n=3 THEN '{"description":"Сохранённое описание_%!","title":"Игнорировать заголовок","alert_id":"a0f8d800-0000-4000-8000-000000000001","actor":{"id":"a0f8d600-0000-4000-8000-000000000001","name":"Историческое имя","role":"representative"},"changes":[{"field":"version","before":9007199254740993,"after":"9007199254740994"},{"field":"status","before":"ASSIGNED","after":"EN_ROUTE"}]}'::jsonb
         WHEN n=5 THEN '{"from_date":"2099-11-09","to_date":"2099-11-10","description":"Перенос на 2099-11-10"}'::jsonb
         ELSE '{}'::jsonb END
FROM (VALUES (1,'DATA_LOADED'),(2,'PLAN_ACTIVATED'),(3,'STATUS_CHANGED'),(4,'ASSIGNMENT_CHANGED'),
    (5,'ORDER_RESCHEDULED'),(6,'COURIER_DELAYED'),(7,'ORDER_REFUSED'),(8,'COURIER_REFUSED'),
    (9,'ALERT_CREATED'),(10,'ALERT_ORDER_RESOLVED'),(11,'ALERT_RESOLVED'),(12,'SCENARIO_RESET')) v(n,t);
-- Equal created_at, different occurred_at; UUID is the only tie-breaker.
UPDATE business_event SET created_at='2099-11-09T10:11:00Z' WHERE id='a0f8dc00-0000-4000-8000-00000000000c';
