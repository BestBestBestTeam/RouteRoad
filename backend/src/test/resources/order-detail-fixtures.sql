-- Detail fixtures are loaded in the test transaction after repository/workday fixtures.

INSERT INTO app_user(id,login,password_hash,name,role,courier_id) VALUES ('a0f8d600-0000-4000-8000-000000000003','detail-representative-b',repeat('a',64),'Detail representative B','representative','a0f8d200-0000-4000-8000-000000000002');

INSERT INTO service_order(id,number,client_name,address,district,product,lat,lon,window_from,window_to,service_duration_s,status,service_date,version) VALUES ('a0f8d300-0000-4000-8000-000000000009','detail-own','Detail client','Detail address','Central','Card',55,82,'2099-11-09T09:00:00Z','2099-11-09T12:00:00Z',600,'ASSIGNED','2099-11-09',2);

INSERT INTO assignment(order_id,plan_version,courier_id,route_id,source_order_version,reason_code,reason_text,decided_by,created_at) VALUES ('a0f8d300-0000-4000-8000-000000000009',9000000000000000001,'a0f8d200-0000-4000-8000-000000000001','a0f8d500-0000-4000-8000-000000000001',1,'NEAREST','Detail assigned','SYSTEM','2099-11-09T09:00:00Z');

UPDATE business_event SET payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || to_jsonb('a0f8d300-0000-4000-8000-000000000009'::text)) WHERE id='a0f8d700-0000-4000-8000-000000000001';

INSERT INTO business_event(id,type,service_date,order_id,courier_id,occurred_at,created_at,reason,payload) VALUES ('a0f8d700-0000-4000-8000-000000000006','COURIER_DELAYED','2099-11-09','a0f8d300-0000-4000-8000-000000000009','a0f8d200-0000-4000-8000-000000000001','2099-11-09T09:04:00Z','2099-11-09T09:04:00Z','Delay','{}');

INSERT INTO alert(id,source_event_id,service_date,courier_id,kind,scope,severity,status,reason_code,reason_text,created_at,deadline_at,resolved_at,resolved_by,resolution_reason) VALUES ('a0f8d800-0000-4000-8000-000000000003','a0f8d700-0000-4000-8000-000000000006','2099-11-09','a0f8d200-0000-4000-8000-000000000001','delay','courier_day','warning','open','DELAY','PRIVATE-OTHER-ORDER','2099-11-09T09:04:00Z','2099-11-09T09:14:00Z',NULL,NULL,NULL);

INSERT INTO alert_order(alert_id,order_id,status,resolved_at,resolved_by,resolution_reason) VALUES ('a0f8d800-0000-4000-8000-000000000003','a0f8d300-0000-4000-8000-000000000009','pending',NULL,NULL,NULL);

INSERT INTO alert(id,source_event_id,service_date,courier_id,kind,scope,severity,status,reason_code,reason_text,created_at,deadline_at,resolved_at,resolved_by,resolution_reason) VALUES ('a0f8d800-0000-4000-8000-000000000004','a0f8d700-0000-4000-8000-000000000006','2099-11-09','a0f8d200-0000-4000-8000-000000000001','delay','order','warning','open','DELAY','Own single alert','2099-11-09T09:04:00Z','2099-11-09T09:14:00Z',NULL,NULL,NULL);

INSERT INTO alert_order(alert_id,order_id,status,resolved_at,resolved_by,resolution_reason) VALUES ('a0f8d800-0000-4000-8000-000000000004','a0f8d300-0000-4000-8000-000000000009','pending',NULL,NULL,NULL);

INSERT INTO alert(id,source_event_id,service_date,courier_id,kind,scope,severity,status,reason_code,reason_text,created_at,deadline_at,resolved_at,resolved_by,resolution_reason) VALUES ('a0f8d800-0000-4000-8000-000000000005','a0f8d700-0000-4000-8000-000000000006','2099-11-09','a0f8d200-0000-4000-8000-000000000002','delay','order','warning','resolved','DELAY','PRIVATE-FOREIGN-COURIER','2099-11-09T09:04:00Z','2099-11-09T09:14:00Z','2099-11-09T09:06:00Z','a0f8d600-0000-4000-8000-000000000001','PRIVATE-FOREIGN-RESOLUTION');

INSERT INTO alert_order(alert_id,order_id,status,resolved_at,resolved_by,resolution_reason) VALUES ('a0f8d800-0000-4000-8000-000000000005','a0f8d300-0000-4000-8000-000000000009','resolved','2099-11-09T09:06:00Z','a0f8d600-0000-4000-8000-000000000001','PRIVATE-FOREIGN-RESOLUTION');

INSERT INTO alert_order(alert_id,order_id,status,resolved_at,resolved_by,resolution_reason) VALUES ('a0f8d800-0000-4000-8000-000000000003','a0f8d300-0000-4000-8000-000000000008','resolved','2099-11-09T09:05:00Z','a0f8d600-0000-4000-8000-000000000001','PRIVATE-OTHER-RESOLUTION');
