package com.routeroad;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("context-smoke")
class RouteRoadApplicationTests {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.routeroad.shared.infrastructure.AppUserRepository users;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.routeroad.couriers.infrastructure.CourierRepository couriers;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.routeroad.simulation.infrastructure.ScenarioStateRepository scenarios;


	@Test
	void contextLoads() {
	}

}
