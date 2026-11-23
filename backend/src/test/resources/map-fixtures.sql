-- Used after repository-fixtures.sql and schedule-fixtures.sql, with rollback or explicit cleanup.
-- A second real office visit gives courier B a two-stop route in the baseline map.
INSERT INTO route_stop(route_id,sequence,type,office_id,arrival_eta,departure_eta,distance_from_prev_m,window_violation_s)
VALUES ('a0f8d500-0000-4000-8000-000000000002',2,'OFFICE','a0f8d100-0000-4000-8000-000000000001','2099-11-09T10:00:00Z','2099-11-09T10:05:00Z',100,0);
UPDATE courier SET is_available=false WHERE id='a0f8d200-0000-4000-8000-000000000002';
INSERT INTO service_order(id,number,client_name,address,product,lat,lon,window_from,window_to,service_duration_s,status,service_date,version)
VALUES ('a0f8d300-0000-4000-8000-000000000007','map-transferred','Transferred client','Transferred address','Card',54.5,81.5,'2099-11-10T09:00:00Z','2099-11-10T12:00:00Z',600,'NEW','2099-11-10',2);
INSERT INTO assignment(order_id,plan_version,courier_id,route_id,source_order_version,reason_code,reason_text,decided_by,created_at)
VALUES ('a0f8d300-0000-4000-8000-000000000007',9000000000000000001,'a0f8d200-0000-4000-8000-000000000001','a0f8d500-0000-4000-8000-000000000001',1,'NEAREST','Прежнее назначение','SYSTEM','2099-11-09T09:00:00Z');
UPDATE business_event SET payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || '"a0f8d300-0000-4000-8000-000000000007"'::jsonb)
WHERE id='a0f8d700-0000-4000-8000-000000000001';
INSERT INTO business_event(id,type,service_date,order_id,occurred_at,created_at,reason,payload)
VALUES ('a0f8d700-0000-4000-8000-000000000004','ORDER_RESCHEDULED','2099-11-09','a0f8d300-0000-4000-8000-000000000007','2099-11-09T10:00:00Z','2099-11-09T10:00:00Z','Перенос','{"from_date":"2099-11-09","to_date":"2099-11-10"}');
INSERT INTO alert(id,source_event_id,service_date,courier_id,kind,scope,severity,status,reason_code,reason_text,created_at,deadline_at) VALUES
('a0f8d800-0000-4000-8000-000000000003','a0f8d700-0000-4000-8000-000000000003','2099-11-09',NULL,'conflict','courier_day','warning','open','CONFLICT','Конфликт','2099-11-09T09:03:00Z','2099-11-09T09:13:00Z'),
('a0f8d800-0000-4000-8000-000000000004','a0f8d700-0000-4000-8000-000000000003','2099-11-10',NULL,'conflict','order','warning','open','CONFLICT','Другой день','2099-11-10T09:03:00Z','2099-11-10T09:13:00Z');
INSERT INTO alert_order(alert_id,order_id,status,resolved_at,resolution_reason) VALUES
('a0f8d800-0000-4000-8000-000000000003','a0f8d300-0000-4000-8000-000000000005','pending',NULL,NULL),
('a0f8d800-0000-4000-8000-000000000003','a0f8d300-0000-4000-8000-000000000004','resolved','2099-11-09T09:05:00Z','Решено'),
('a0f8d800-0000-4000-8000-000000000004','a0f8d300-0000-4000-8000-000000000007','pending',NULL,NULL);
