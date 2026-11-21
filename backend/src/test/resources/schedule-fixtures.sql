-- Applied after repository-fixtures.sql. The stale unassigned fixture stop is deliberately removed.
DELETE FROM route_stop WHERE route_id='a0f8d500-0000-4000-8000-000000000001' AND sequence=3;
UPDATE courier SET position_lat=55.123,position_lon=82.456,position_at='2099-11-09T09:00:00Z'
WHERE id='a0f8d200-0000-4000-8000-000000000001';
UPDATE office SET lat=56,lon=83 WHERE id='a0f8d100-0000-4000-8000-000000000002';
INSERT INTO office(id,name,address,lat,lon) VALUES
('a0f8d100-0000-4000-8000-000000000003','Unused office','Unused address',57,84);
INSERT INTO courier(id,name,office_id,shift_start,shift_end,is_available) VALUES
('a0f8d200-0000-4000-8000-000000000003','Empty courier','a0f8d100-0000-4000-8000-000000000001','2099-11-09T09:00:00Z','2099-11-09T18:00:00Z',true);
INSERT INTO service_order(id,number,client_name,address,product,lat,lon,window_from,window_to,service_duration_s,status,service_date,version) VALUES
('a0f8d300-0000-4000-8000-000000000004','schedule-4','Client 4','Address 4','Card',55.04,82.04,'2099-11-09T11:00:00Z','2099-11-09T12:00:00Z',600,'IN_PROGRESS','2099-11-09',9007199254740993),
('a0f8d300-0000-4000-8000-000000000005','schedule-5','Client 5','Address 5','Card',55.05,82.05,'2099-11-09T09:00:00Z','2099-11-09T10:00:00Z',600,'ASSIGNED','2099-11-09',1),
('a0f8d300-0000-4000-8000-000000000006','schedule-6','Client 6','Address 6','Card',55.06,82.06,'2099-11-09T09:00:00Z','2099-11-09T12:00:00Z',600,'COMPLETED','2099-11-09',1);
INSERT INTO assignment(order_id,plan_version,courier_id,route_id,source_order_version,reason_code,reason_text,decided_by,created_at) VALUES
('a0f8d300-0000-4000-8000-000000000004',9000000000000000001,'a0f8d200-0000-4000-8000-000000000001','a0f8d500-0000-4000-8000-000000000001',9007199254740993,'NEAREST','Ближайший представитель','SYSTEM','2099-11-09T09:00:00Z'),
('a0f8d300-0000-4000-8000-000000000005',9000000000000000001,'a0f8d200-0000-4000-8000-000000000001','a0f8d500-0000-4000-8000-000000000001',1,'FREE_WINDOW','Свободное окно','SYSTEM','2099-11-09T09:00:00Z'),
('a0f8d300-0000-4000-8000-000000000006',9000000000000000001,'a0f8d200-0000-4000-8000-000000000001','a0f8d500-0000-4000-8000-000000000001',1,'OPTIMIZED','Оптимальный маршрут','SYSTEM','2099-11-09T09:00:00Z');
UPDATE business_event SET payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') ||
 '["a0f8d300-0000-4000-8000-000000000004","a0f8d300-0000-4000-8000-000000000005","a0f8d300-0000-4000-8000-000000000006"]'::jsonb)
WHERE id='a0f8d700-0000-4000-8000-000000000001';
-- Inserted out of order; only sequence defines the itinerary.
INSERT INTO route_stop(route_id,sequence,type,order_id,office_id,arrival_eta,departure_eta,distance_from_prev_m,window_violation_s) VALUES
('a0f8d500-0000-4000-8000-000000000001',6,'OFFICE',NULL,'a0f8d100-0000-4000-8000-000000000002','2099-11-09T12:00:00Z','2099-11-09T12:05:00Z',100,0),
('a0f8d500-0000-4000-8000-000000000001',4,'ORDER','a0f8d300-0000-4000-8000-000000000004',NULL,'2099-11-09T11:00:00Z','2099-11-09T11:10:00Z',100,0),
('a0f8d500-0000-4000-8000-000000000001',3,'ORDER','a0f8d300-0000-4000-8000-000000000005',NULL,'2099-11-09T10:02:00Z','2099-11-09T10:12:00Z',100,120),
('a0f8d500-0000-4000-8000-000000000001',5,'ORDER','a0f8d300-0000-4000-8000-000000000006',NULL,'2099-11-09T11:30:00Z','2099-11-09T11:40:00Z',100,0);
UPDATE route SET total_distance_m=450,violated_windows=1 WHERE id='a0f8d500-0000-4000-8000-000000000001';
