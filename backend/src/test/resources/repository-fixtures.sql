-- Executed by @Sql inside each test transaction; every row is rolled back.
-- IDs and plan versions are explicit, so the planning sequence is not consumed.
INSERT INTO office(id, name, address, lat, lon) VALUES
('a0f8d100-0000-4000-8000-000000000001', 'Repo office A', 'Repo address A', 55, 82),
('a0f8d100-0000-4000-8000-000000000002', 'Repo office B', 'Repo address B', 55, 82);

INSERT INTO courier(id, name, office_id, shift_start, shift_end, is_available) VALUES
('a0f8d200-0000-4000-8000-000000000001', 'Repo courier A', 'a0f8d100-0000-4000-8000-000000000001', '2099-11-09T09:00:00Z', '2099-11-09T18:00:00Z', true),
('a0f8d200-0000-4000-8000-000000000002', 'Repo courier B', 'a0f8d100-0000-4000-8000-000000000002', '2099-11-09T09:00:00Z', '2099-11-09T18:00:00Z', true);

INSERT INTO service_order(id, number, client_name, address, product, lat, lon, window_from, window_to, service_duration_s, status, service_date, version) VALUES
('a0f8d300-0000-4000-8000-000000000001', 'repo-A', 'Repo client A', 'Repo address A', 'Card', 55, 82, '2099-11-09T09:00:00Z', '2099-11-09T12:00:00Z', 600, 'ASSIGNED', '2099-11-09', 2),
('a0f8d300-0000-4000-8000-000000000002', 'repo-B', 'Repo client B', 'Repo address B', 'Card', 55, 82, '2099-11-09T09:00:00Z', '2099-11-09T12:00:00Z', 600, 'UNASSIGNED', '2099-11-09', 1),
('a0f8d300-0000-4000-8000-000000000003', 'repo-C', 'Repo client C', 'Repo address C', 'Card', 55, 82, '2099-11-10T09:00:00Z', '2099-11-10T12:00:00Z', 600, 'NEW', '2099-11-10', 1);

INSERT INTO planning_run(plan_version, request_id, run_type, event_type, algorithm, plan_date, model_now, created_at, metrics) VALUES
(9000000000000000001, 'a0f8d400-0000-4000-8000-000000000001', 'ASSIGN', NULL, 'greedy', '2099-11-09', '2099-11-09T09:00:00Z', '2099-11-09T09:00:00Z', '{"total_distance_m":100,"violated_windows":0,"unassigned_count":0,"solve_time_ms":1}'),
(9000000000000000002, 'a0f8d400-0000-4000-8000-000000000002', 'REASSIGN', 'AUTO_REASSIGN_REQUEST', 'ortools', '2099-11-09', '2099-11-09T09:02:00Z', '2099-11-09T09:02:00Z', '{"total_distance_m":0,"violated_windows":0,"unassigned_count":1,"solve_time_ms":2}'),
(9000000000000000003, 'a0f8d400-0000-4000-8000-000000000003', 'REASSIGN', 'ORDER_REFUSED', 'ortools', '2099-11-09', '2099-11-09T09:04:00Z', '2099-11-09T09:04:00Z', '{"total_distance_m":200,"violated_windows":0,"unassigned_count":0,"solve_time_ms":3}');

INSERT INTO route(id, courier_id, plan_date, plan_version, is_active, algorithm, total_distance_m, violated_windows, created_at) VALUES
('a0f8d500-0000-4000-8000-000000000001', 'a0f8d200-0000-4000-8000-000000000001', '2099-11-09', 9000000000000000001, true, 'greedy', 100, 0, '2099-11-09T09:00:00Z'),
('a0f8d500-0000-4000-8000-000000000002', 'a0f8d200-0000-4000-8000-000000000002', '2099-11-09', 9000000000000000002, true, 'ortools', 0, 0, '2099-11-09T09:02:00Z'),
('a0f8d500-0000-4000-8000-000000000003', 'a0f8d200-0000-4000-8000-000000000001', '2099-11-09', 9000000000000000003, false, 'ortools', 200, 0, '2099-11-09T09:04:00Z');

-- Intentionally not inserted in sequence order.
INSERT INTO route_stop(route_id, sequence, type, order_id, office_id, arrival_eta, departure_eta, distance_from_prev_m, window_violation_s) VALUES
('a0f8d500-0000-4000-8000-000000000001', 3, 'ORDER', 'a0f8d300-0000-4000-8000-000000000002', NULL, '2099-11-09T10:00:00Z', '2099-11-09T10:10:00Z', 50, 0),
('a0f8d500-0000-4000-8000-000000000001', 1, 'OFFICE', NULL, 'a0f8d100-0000-4000-8000-000000000001', '2099-11-09T09:00:00Z', '2099-11-09T09:00:00Z', 0, 0),
('a0f8d500-0000-4000-8000-000000000001', 2, 'ORDER', 'a0f8d300-0000-4000-8000-000000000001', NULL, '2099-11-09T09:30:00Z', '2099-11-09T09:40:00Z', 50, 0),
('a0f8d500-0000-4000-8000-000000000002', 1, 'OFFICE', NULL, 'a0f8d100-0000-4000-8000-000000000002', '2099-11-09T09:02:00Z', '2099-11-09T09:02:00Z', 0, 0),
('a0f8d500-0000-4000-8000-000000000003', 1, 'OFFICE', NULL, 'a0f8d100-0000-4000-8000-000000000001', '2099-11-09T09:04:00Z', '2099-11-09T09:04:00Z', 0, 0);

INSERT INTO assignment(order_id, plan_version, courier_id, route_id, source_order_version, reason_code, reason_text, decided_by, created_at) VALUES
('a0f8d300-0000-4000-8000-000000000001', 9000000000000000001, 'a0f8d200-0000-4000-8000-000000000001', 'a0f8d500-0000-4000-8000-000000000001', 1, 'NEAREST', 'Repo initial decision', 'SYSTEM', '2099-11-09T09:00:00Z'),
('a0f8d300-0000-4000-8000-000000000001', 9000000000000000003, 'a0f8d200-0000-4000-8000-000000000001', 'a0f8d500-0000-4000-8000-000000000003', 2, 'OPTIMIZED', 'Repo unconfirmed decision', 'SYSTEM', '2099-11-09T09:04:00Z'),
('a0f8d300-0000-4000-8000-000000000002', 9000000000000000002, NULL, NULL, 1, 'NO_CAPACITY', 'Repo unassigned decision', 'SYSTEM', '2099-11-09T09:02:00Z');

INSERT INTO app_user(id, login, password_hash, name, role, courier_id) VALUES
('a0f8d600-0000-4000-8000-000000000001', 'repo-dispatcher', repeat('a',64), 'Repo dispatcher', 'dispatcher', NULL),
('a0f8d600-0000-4000-8000-000000000002', 'repo-representative', repeat('a',64), 'Repo representative', 'representative', 'a0f8d200-0000-4000-8000-000000000001');

INSERT INTO business_event(id, type, service_date, order_id, courier_id, actor_id, occurred_at, created_at, reason, payload) VALUES
('a0f8d700-0000-4000-8000-000000000001', 'PLAN_ACTIVATED', '2099-11-09', 'a0f8d300-0000-4000-8000-000000000001', 'a0f8d200-0000-4000-8000-000000000001', 'a0f8d600-0000-4000-8000-000000000001', '2099-11-09T09:01:00Z', '2099-11-09T09:01:00Z', 'Repo activation', '{"plan_version":"9000000000000000001","order_ids":["a0f8d300-0000-4000-8000-000000000001"]}'),
('a0f8d700-0000-4000-8000-000000000002', 'PLAN_ACTIVATED', '2099-11-09', 'a0f8d300-0000-4000-8000-000000000002', 'a0f8d200-0000-4000-8000-000000000002', NULL, '2099-11-09T09:02:00Z', '2099-11-09T09:02:00Z', 'Repo activation', '{"plan_version":"9000000000000000002","order_ids":["a0f8d300-0000-4000-8000-000000000002"]}'),
('a0f8d700-0000-4000-8000-000000000003', 'ORDER_REFUSED', '2099-11-09', 'a0f8d300-0000-4000-8000-000000000001', 'a0f8d200-0000-4000-8000-000000000001', 'a0f8d600-0000-4000-8000-000000000002', '2099-11-09T09:03:00Z', '2099-11-09T09:03:00Z', 'Repo refusal', '{"base_version":"1","version":"2"}');

INSERT INTO alert(id, source_event_id, service_date, courier_id, kind, scope, severity, status, reason_code, reason_text, created_at, deadline_at, resolved_at, resolved_by, resolution_reason) VALUES
('a0f8d800-0000-4000-8000-000000000001', 'a0f8d700-0000-4000-8000-000000000003', '2099-11-09', 'a0f8d200-0000-4000-8000-000000000001', 'refusal', 'order', 'critical', 'open', 'ORDER_REFUSED', 'Repo refusal', '2099-11-09T09:03:00Z', '2099-11-09T09:13:00Z', NULL, NULL, NULL),
('a0f8d800-0000-4000-8000-000000000002', 'a0f8d700-0000-4000-8000-000000000002', '2099-11-09', NULL, 'conflict', 'order', 'warning', 'resolved', 'NO_CAPACITY', 'Repo conflict', '2099-11-09T09:02:00Z', '2099-11-09T09:12:00Z', '2099-11-09T09:05:00Z', 'a0f8d600-0000-4000-8000-000000000001', 'Repo resolution');

INSERT INTO alert_order(alert_id, order_id, status, resolved_at, resolved_by, resolution_reason) VALUES
('a0f8d800-0000-4000-8000-000000000001', 'a0f8d300-0000-4000-8000-000000000001', 'pending', NULL, NULL, NULL),
('a0f8d800-0000-4000-8000-000000000002', 'a0f8d300-0000-4000-8000-000000000002', 'resolved', '2099-11-09T09:05:00Z', 'a0f8d600-0000-4000-8000-000000000001', 'Repo resolution');

INSERT INTO api_command(id, scenario_id, actor_id, idempotency_key, type, request_hash, request, status, result, error, created_at, updated_at) VALUES
('a0f8d900-0000-4000-8000-000000000001', 'a0f8db00-0000-4000-8000-000000000002', NULL, 'a0f8da00-0000-4000-8000-000000000001', 'REPO_PROBE', repeat('b',64), '{}', 'pending', NULL, NULL, '2099-11-09T09:00:00Z', '2099-11-09T09:00:00Z'),
('a0f8d900-0000-4000-8000-000000000002', 'a0f8db00-0000-4000-8000-000000000001', 'a0f8d600-0000-4000-8000-000000000001', 'a0f8da00-0000-4000-8000-000000000001', 'REPO_PROBE', repeat('b',64), '{}', 'succeeded', '{}', NULL, '2099-11-09T09:00:00Z', '2099-11-09T09:00:00Z');

INSERT INTO scenario_state(scenario_id, seed, config, service_date, model_anchor_at, real_anchor_at, speed, revision) VALUES
('a0f8db00-0000-4000-8000-000000000001', 321, '{}', '2099-11-09', '2099-11-09T09:00:00Z', '2099-11-09T09:00:00Z', 1, 1);
