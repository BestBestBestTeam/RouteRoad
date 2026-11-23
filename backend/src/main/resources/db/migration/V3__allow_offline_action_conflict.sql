ALTER TABLE business_event
    DROP CONSTRAINT business_event_type_check,
    ADD CONSTRAINT business_event_type_check CHECK (type IN (
        'DATA_LOADED',
        'PLAN_ACTIVATED',
        'STATUS_CHANGED',
        'ASSIGNMENT_CHANGED',
        'ORDER_RESCHEDULED',
        'COURIER_DELAYED',
        'ORDER_REFUSED',
        'COURIER_REFUSED',
        'ALERT_CREATED',
        'ALERT_ORDER_RESOLVED',
        'ALERT_RESOLVED',
        'OFFLINE_ACTION_CONFLICT',
        'SCENARIO_RESET'
    ));
