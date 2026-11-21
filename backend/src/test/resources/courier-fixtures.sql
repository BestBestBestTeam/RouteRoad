-- Executed after repository/workday fixtures inside each rollback-only test transaction.

INSERT INTO courier(id,name,office_id,shift_start,shift_end,position_lat,position_lon,position_at,is_available) VALUES ('a0f8d200-0000-4000-8000-000000000003','Same courier','a0f8d100-0000-4000-8000-000000000001','2099-11-09T09:00:00Z','2099-11-09T18:00:00Z',55.123,82.456,'2099-11-09T09:00:00Z',true);

INSERT INTO courier(id,name,office_id,shift_start,shift_end,position_lat,position_lon,position_at,is_available) VALUES ('a0f8d200-0000-4000-8000-000000000004','Same courier','a0f8d100-0000-4000-8000-000000000002','2099-11-09T09:00:00Z','2099-11-09T18:00:00Z',NULL,NULL,NULL,false);

INSERT INTO courier(id,name,office_id,shift_start,shift_end,position_lat,position_lon,position_at,is_available) VALUES ('a0f8d200-0000-4000-8000-000000000005','Иван Тест_%!','a0f8d100-0000-4000-8000-000000000001','2099-11-09T09:00:00Z','2099-11-09T18:00:00Z',NULL,NULL,NULL,true);

INSERT INTO route(id,courier_id,plan_date,plan_version,is_active,algorithm,total_distance_m,violated_windows,created_at) VALUES ('a0f8d500-0000-4000-8000-000000000004','a0f8d200-0000-4000-8000-000000000003','2099-11-09',9000000000000000001,true,'greedy',0,0,'2099-11-09T09:00:00Z');

INSERT INTO route(id,courier_id,plan_date,plan_version,is_active,algorithm,total_distance_m,violated_windows,created_at) VALUES ('a0f8d500-0000-4000-8000-000000000005','a0f8d200-0000-4000-8000-000000000004','2099-11-09',9000000000000000002,true,'greedy',0,0,'2099-11-09T09:00:00Z');

INSERT INTO service_order(id,number,client_name,address,product,lat,lon,window_from,window_to,service_duration_s,status,service_date,version) VALUES ('a0f8d300-0000-4000-8000-000000000009','courier-test-9','Client','Address','Card',55,82,'2099-11-09T09:00:00Z','2099-11-09T12:00:00Z',600,'CANCELLED','2099-11-09',2);

INSERT INTO assignment(order_id,plan_version,courier_id,route_id,source_order_version,reason_code,reason_text,decided_by,created_at) VALUES ('a0f8d300-0000-4000-8000-000000000009',9000000000000000001,'a0f8d200-0000-4000-8000-000000000001','a0f8d500-0000-4000-8000-000000000001',1,'OPTIMIZED','Courier test decision','SYSTEM','2099-11-09T09:00:00Z');

UPDATE business_event SET payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || to_jsonb('a0f8d300-0000-4000-8000-000000000009'::text)) WHERE type='PLAN_ACTIVATED' AND payload->>'plan_version'='9000000000000000001';

INSERT INTO service_order(id,number,client_name,address,product,lat,lon,window_from,window_to,service_duration_s,status,service_date,version) VALUES ('a0f8d300-0000-4000-8000-00000000000a','courier-test-10','Client','Address','Card',55,82,'2099-11-09T09:00:00Z','2099-11-09T12:00:00Z',600,'IN_PROGRESS','2099-11-09',2);

INSERT INTO assignment(order_id,plan_version,courier_id,route_id,source_order_version,reason_code,reason_text,decided_by,created_at) VALUES ('a0f8d300-0000-4000-8000-00000000000a',9000000000000000001,'a0f8d200-0000-4000-8000-000000000001','a0f8d500-0000-4000-8000-000000000001',1,'OPTIMIZED','Courier test decision','SYSTEM','2099-11-09T09:00:00Z');

UPDATE business_event SET payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || to_jsonb('a0f8d300-0000-4000-8000-00000000000a'::text)) WHERE type='PLAN_ACTIVATED' AND payload->>'plan_version'='9000000000000000001';

INSERT INTO service_order(id,number,client_name,address,product,lat,lon,window_from,window_to,service_duration_s,status,service_date,version) VALUES ('a0f8d300-0000-4000-8000-00000000000b','courier-test-11','Client','Address','Card',55,82,'2099-11-09T09:00:00Z','2099-11-09T12:00:00Z',600,'NEW','2099-11-09',2);

INSERT INTO assignment(order_id,plan_version,courier_id,route_id,source_order_version,reason_code,reason_text,decided_by,created_at) VALUES ('a0f8d300-0000-4000-8000-00000000000b',9000000000000000001,'a0f8d200-0000-4000-8000-000000000003','a0f8d500-0000-4000-8000-000000000004',1,'OPTIMIZED','Courier test decision','SYSTEM','2099-11-09T09:00:00Z');

UPDATE business_event SET payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || to_jsonb('a0f8d300-0000-4000-8000-00000000000b'::text)) WHERE type='PLAN_ACTIVATED' AND payload->>'plan_version'='9000000000000000001';

INSERT INTO service_order(id,number,client_name,address,product,lat,lon,window_from,window_to,service_duration_s,status,service_date,version) VALUES ('a0f8d300-0000-4000-8000-00000000000c','courier-test-12','Client','Address','Card',55,82,'2099-11-09T09:00:00Z','2099-11-09T12:00:00Z',600,'UNASSIGNED','2099-11-09',2);

INSERT INTO assignment(order_id,plan_version,courier_id,route_id,source_order_version,reason_code,reason_text,decided_by,created_at) VALUES ('a0f8d300-0000-4000-8000-00000000000c',9000000000000000002,'a0f8d200-0000-4000-8000-000000000004','a0f8d500-0000-4000-8000-000000000005',1,'OPTIMIZED','Courier test decision','SYSTEM','2099-11-09T09:00:00Z');

UPDATE business_event SET payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || to_jsonb('a0f8d300-0000-4000-8000-00000000000c'::text)) WHERE type='PLAN_ACTIVATED' AND payload->>'plan_version'='9000000000000000002';

INSERT INTO business_event(id,type,service_date,order_id,courier_id,occurred_at,created_at,reason,payload) VALUES ('a0f8d700-0000-4000-8000-000000000006','ALERT_CREATED','2099-11-09','a0f8d300-0000-4000-8000-000000000001','a0f8d200-0000-4000-8000-000000000001','2099-11-09T09:00:00Z','2099-11-09T09:00:00Z','Courier alert','{}');

INSERT INTO business_event(id,type,service_date,order_id,courier_id,occurred_at,created_at,reason,payload) VALUES ('a0f8d700-0000-4000-8000-000000000007','ALERT_CREATED','2099-11-09','a0f8d300-0000-4000-8000-000000000002','a0f8d200-0000-4000-8000-000000000002','2099-11-09T09:00:00Z','2099-11-09T09:00:00Z','Courier alert','{}');

INSERT INTO business_event(id,type,service_date,order_id,courier_id,occurred_at,created_at,reason,payload) VALUES ('a0f8d700-0000-4000-8000-000000000008','ALERT_CREATED','2099-11-09','a0f8d300-0000-4000-8000-000000000001',NULL,'2099-11-09T09:00:00Z','2099-11-09T09:00:00Z','Courier alert','{}');

INSERT INTO business_event(id,type,service_date,order_id,courier_id,occurred_at,created_at,reason,payload) VALUES ('a0f8d700-0000-4000-8000-000000000009','ALERT_CREATED','2099-11-10','a0f8d300-0000-4000-8000-000000000003','a0f8d200-0000-4000-8000-000000000003','2099-11-10T09:00:00Z','2099-11-10T09:00:00Z','Courier alert','{}');

INSERT INTO alert(id,source_event_id,service_date,courier_id,kind,scope,severity,status,reason_code,reason_text,created_at,deadline_at) VALUES ('a0f8d800-0000-4000-8000-000000000003','a0f8d700-0000-4000-8000-000000000006','2099-11-09','a0f8d200-0000-4000-8000-000000000001','conflict','courier_day','warning','open','CONFLICT','Courier alert','2099-11-09T09:00:00Z','2099-11-09T09:10:00Z');

INSERT INTO alert_order(alert_id,order_id,status) VALUES ('a0f8d800-0000-4000-8000-000000000003','a0f8d300-0000-4000-8000-000000000001','pending');

INSERT INTO alert_order(alert_id,order_id,status) VALUES ('a0f8d800-0000-4000-8000-000000000003','a0f8d300-0000-4000-8000-00000000000a','pending');

INSERT INTO alert(id,source_event_id,service_date,courier_id,kind,scope,severity,status,reason_code,reason_text,created_at,deadline_at) VALUES ('a0f8d800-0000-4000-8000-000000000004','a0f8d700-0000-4000-8000-000000000007','2099-11-09','a0f8d200-0000-4000-8000-000000000002','conflict','order','warning','open','CONFLICT','Courier alert','2099-11-09T09:00:00Z','2099-11-09T09:10:00Z');

INSERT INTO alert_order(alert_id,order_id,status) VALUES ('a0f8d800-0000-4000-8000-000000000004','a0f8d300-0000-4000-8000-000000000002','pending');

INSERT INTO alert(id,source_event_id,service_date,courier_id,kind,scope,severity,status,reason_code,reason_text,created_at,deadline_at) VALUES ('a0f8d800-0000-4000-8000-000000000005','a0f8d700-0000-4000-8000-000000000008','2099-11-09',NULL,'conflict','order','warning','open','CONFLICT','Courier alert','2099-11-09T09:00:00Z','2099-11-09T09:10:00Z');

INSERT INTO alert_order(alert_id,order_id,status) VALUES ('a0f8d800-0000-4000-8000-000000000005','a0f8d300-0000-4000-8000-000000000001','pending');

INSERT INTO alert(id,source_event_id,service_date,courier_id,kind,scope,severity,status,reason_code,reason_text,created_at,deadline_at) VALUES ('a0f8d800-0000-4000-8000-000000000006','a0f8d700-0000-4000-8000-000000000009','2099-11-10','a0f8d200-0000-4000-8000-000000000003','conflict','order','warning','open','CONFLICT','Courier alert','2099-11-10T09:00:00Z','2099-11-10T09:10:00Z');

INSERT INTO alert_order(alert_id,order_id,status) VALUES ('a0f8d800-0000-4000-8000-000000000006','a0f8d300-0000-4000-8000-000000000003','pending');
