package com.routeroad.couriers.infrastructure;

import com.routeroad.couriers.api.CourierView;
import com.routeroad.couriers.application.CourierReader;
import com.routeroad.shared.api.Position;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class JpaCourierReader implements CourierReader {
    
    private final CourierRepository couriers;
    
    public JpaCourierReader(CourierRepository couriers) { this.couriers = couriers; }
    @Override public Result find(Filter filter, int pageSize, long offset) {
        long total = couriers.countCourierList(filter.date(), filter.search(), filter.available(),
                filter.hasOpenAlerts(), filter.hasWorkload());
        
        var rows = couriers.findCourierList(filter.date(), filter.search(), filter.available(),
                filter.hasOpenAlerts(), filter.hasWorkload(), pageSize, offset);
        var views = rows.stream().map(row -> new CourierView(row.getId(), row.getName(), row.getOfficeId(),
                row.getShiftStart(), row.getShiftEnd(), Position.fromNullable(row.getPositionLat(), row.getPositionLon(), row.getPositionAt()),
                row.getAvailable(), Math.toIntExact(row.getTotalOrders()), Math.toIntExact(row.getCompletedOrders()),
                
                Math.toIntExact(row.getOpenAlertCount()), List.<String>of())).toList();

        return new Result(views, total);
    }
}
