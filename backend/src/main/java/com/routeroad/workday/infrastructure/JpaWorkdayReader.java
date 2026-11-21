package com.routeroad.workday.infrastructure;

import com.routeroad.couriers.infrastructure.CourierRepository;
import com.routeroad.execution.domain.AlertStatus;
import com.routeroad.execution.infrastructure.AlertRepository;
import com.routeroad.offices.infrastructure.OfficeRepository;
import com.routeroad.orders.infrastructure.ServiceOrderRepository;
import com.routeroad.planning.infrastructure.RouteRepository;
import com.routeroad.workday.application.WorkdayReader;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.List;

@Component
public class JpaWorkdayReader implements WorkdayReader {
    private final ServiceOrderRepository orders;
    private final CourierRepository couriers;
    private final AlertRepository alerts;
    private final RouteRepository routes;
    private final OfficeRepository offices;

    public JpaWorkdayReader(ServiceOrderRepository orders,
                            CourierRepository couriers, AlertRepository alerts,
                            RouteRepository routes, OfficeRepository offices) {
        this.orders = orders;
        this.couriers = couriers;
        this.alerts = alerts;
        this.routes = routes;
        this.offices = offices;
    }

    @Override
    public Counts count(LocalDate date) {
        var count = orders.findWorkdayOrderCounts(date);
        return new Counts(count.getOrdersTotal(), count.getOrdersAssigned(), count.getOrdersCompleted(),
                couriers.count(), alerts.countByServiceDateAndStatus(date, AlertStatus.OPEN),
                routes.existsConfirmedActiveByPlanDate(date));
    }

    @Override
    public List<Office> findOffices() {
        return offices.findAll(Sort.by("name", "id")).stream().map(office -> new Office(
                office.getId(), office.getName(), office.getAddress(), office.getLat(), office.getLon())).toList();
    }
}
