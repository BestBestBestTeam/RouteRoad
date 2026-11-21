-- All rows belong to the test transaction and are rolled back.
INSERT INTO scenario_state(scenario_id,seed,config,service_date,model_anchor_at,real_anchor_at,speed,revision)
VALUES ('a0f8db00-0000-4000-8000-000000000001',NULL,'{}','2099-11-09','2099-11-09T09:00:00Z','2099-11-09T09:00:00Z',0,1);
INSERT INTO app_user(id,login,password_hash,name,role,courier_id)
VALUES ('a0f8d600-0000-4000-8000-000000000001','workday-empty-dispatcher',repeat('a',64),'Empty dispatcher','dispatcher',NULL);
