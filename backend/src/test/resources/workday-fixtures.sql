-- Additional workday data; executed after repository-fixtures.sql, entirely inside @Rollback.

INSERT INTO service_order(id,number,client_name,address,product,lat,lon,window_from,window_to,service_duration_s,status,service_date,version) VALUES
('a0f8d300-0000-4000-8000-000000000004','workday-4','Client','Address','Card',55,82,'2099-11-09T09:00:00Z','2099-11-09T12:00:00Z',600,'COMPLETED','2099-11-09',4),
('a0f8d300-0000-4000-8000-000000000005','workday-5','Client','Address','Card',55,82,'2099-11-10T09:00:00Z','2099-11-10T12:00:00Z',600,'NEW','2099-11-10',4),
('a0f8d300-0000-4000-8000-000000000006','workday-6','Client','Address','Card',55,82,'2099-11-09T09:00:00Z','2099-11-09T12:00:00Z',600,'ASSIGNED','2099-11-09',4),
('a0f8d300-0000-4000-8000-000000000007','workday-7','Client','Address','Card',55,82,'2099-11-09T09:00:00Z','2099-11-09T12:00:00Z',600,'UNASSIGNED','2099-11-09',4),
('a0f8d300-0000-4000-8000-000000000008','workday-8','Client','Address','Card',55,82,'2099-11-09T09:00:00Z','2099-11-09T12:00:00Z',600,'ASSIGNED','2099-11-09',4);

INSERT INTO assignment(order_id,plan_version,courier_id,route_id,source_order_version,reason_code,reason_text,decided_by,created_at) VALUES
('a0f8d300-0000-4000-8000-000000000004',9000000000000000002,'a0f8d200-0000-4000-8000-000000000002','a0f8d500-0000-4000-8000-000000000002',1,'OPTIMIZED','Workday decision','SYSTEM','2099-11-09T09:00:00Z'),
('a0f8d300-0000-4000-8000-000000000005',9000000000000000001,'a0f8d200-0000-4000-8000-000000000001','a0f8d500-0000-4000-8000-000000000001',1,'NEAREST','Workday decision','SYSTEM','2099-11-09T09:00:00Z'),
('a0f8d300-0000-4000-8000-000000000006',9000000000000000003,'a0f8d200-0000-4000-8000-000000000001','a0f8d500-0000-4000-8000-000000000003',2,'OPTIMIZED','Workday decision','SYSTEM','2099-11-09T09:00:00Z'),
('a0f8d300-0000-4000-8000-000000000007',9000000000000000001,'a0f8d200-0000-4000-8000-000000000001','a0f8d500-0000-4000-8000-000000000001',1,'NEAREST','Workday decision','SYSTEM','2099-11-09T09:00:00Z'),
('a0f8d300-0000-4000-8000-000000000007',9000000000000000002,NULL,NULL,2,'NO_CAPACITY','Workday decision','SYSTEM','2099-11-09T09:00:00Z'),
('a0f8d300-0000-4000-8000-000000000008',9000000000000000002,'a0f8d200-0000-4000-8000-000000000002','a0f8d500-0000-4000-8000-000000000002',1,'OPTIMIZED','Workday decision','SYSTEM','2099-11-09T09:00:00Z');

UPDATE business_event SET payload='{"plan_version": "9000000000000000001", "order_ids": ["a0f8d300-0000-4000-8000-000000000001", "a0f8d300-0000-4000-8000-000000000005", "a0f8d300-0000-4000-8000-000000000007"]}'::jsonb WHERE id='a0f8d700-0000-4000-8000-000000000001';

UPDATE business_event SET payload='{"plan_version": "9000000000000000002", "order_ids": ["a0f8d300-0000-4000-8000-000000000002", "a0f8d300-0000-4000-8000-000000000004", "a0f8d300-0000-4000-8000-000000000007"]}'::jsonb WHERE id='a0f8d700-0000-4000-8000-000000000002';

INSERT INTO business_event(id,type,service_date,order_id,occurred_at,created_at,reason,payload) VALUES ('a0f8d700-0000-4000-8000-000000000004','ORDER_RESCHEDULED','2099-11-09','a0f8d300-0000-4000-8000-000000000005','2099-11-09T10:00:00Z','2099-11-09T10:00:00Z','Transfer','{"from_date": "2099-11-09", "to_date": "2099-11-10", "changes": [{"field": "service_date", "before": "2099-11-09", "after": "2099-11-10"}, {"field": "status", "before": "UNASSIGNED", "after": "NEW"}]}');

INSERT INTO business_event(id,type,service_date,order_id,occurred_at,created_at,reason,payload) VALUES ('a0f8d700-0000-4000-8000-000000000005','ORDER_RESCHEDULED','2099-11-09','a0f8d300-0000-4000-8000-000000000005','2099-11-09T10:00:00Z','2099-11-09T10:00:00Z','Transfer','{"from_date": "2099-11-09", "to_date": "2099-11-10", "changes": [{"field": "service_date", "before": "2099-11-09", "after": "2099-11-10"}, {"field": "status", "before": "UNASSIGNED", "after": "NEW"}]}');

UPDATE courier SET is_available=false WHERE id='a0f8d200-0000-4000-8000-000000000002';
