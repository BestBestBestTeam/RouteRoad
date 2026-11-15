package com.routeroad;

import com.routeroad.execution.infrastructure.BusinessEventRepository;
import com.routeroad.planning.infrastructure.AssignmentId;
import com.routeroad.planning.infrastructure.AssignmentRepository;
import com.routeroad.planning.infrastructure.PlanningRunRepository;
import com.routeroad.planning.infrastructure.RouteRepository;
import com.routeroad.planning.infrastructure.RouteStopId;
import com.routeroad.planning.infrastructure.RouteStopRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.core.support.AbstractRepositoryMetadata;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalRepositoryContractTests {

    static Stream<Arguments> restrictedRepositories() {
        return Stream.of(
                Arguments.of(PlanningRunRepository.class, Long.class, Set.of()),
                Arguments.of(RouteStopRepository.class, RouteStopId.class, Set.of()),
                Arguments.of(AssignmentRepository.class, AssignmentId.class, Set.of()),
                Arguments.of(RouteRepository.class, UUID.class, Set.of("setActive")),
                Arguments.of(BusinessEventRepository.class, UUID.class, Set.of("save")));
    }

    @ParameterizedTest
    @MethodSource("restrictedRepositories")
    void historicalRepositoriesExposeOnlyAllowedOperations(
            Class<?> repositoryType, Class<?> idType, Set<String> allowedWrites) {
        assertThat(CrudRepository.class.isAssignableFrom(repositoryType)).isFalse();
        assertThat(AbstractRepositoryMetadata.getMetadata(repositoryType).getIdType()).isEqualTo(idType);
        assertThat(Arrays.stream(repositoryType.getMethods()).map(method -> method.getName()))
                .allMatch(name -> name.startsWith("find") || name.startsWith("exists")
                        || name.startsWith("count") || allowedWrites.contains(name));
    }
}
