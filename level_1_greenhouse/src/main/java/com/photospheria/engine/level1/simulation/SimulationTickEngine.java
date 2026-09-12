package com.photospheria.engine.level1.simulation;

import java.util.ArrayList;
import java.util.List;

public class SimulationTickEngine {

    private static final String VON_NEUMANN_SPATIAL_PROPAGATION_GEOMETRY = "VonNeumann";
    private static final String MOORE_SPATIAL_PROPAGATION_GEOMETRY = "Moore";
    private static final String ROW_SPATIAL_PROPAGATION_GEOMETRY = "Row";
    private static final String COLUMN_SPATIAL_PROPAGATION_GEOMETRY = "Column";
    private static final String CROSS_HATCH_SPATIAL_PROPAGATION_GEOMETRY = "CrossHatch";

    public record SimulationCoordinate(
            int targetVerticalRowCoordinate,
            int targetHorizontalColumnCoordinate) {
    }

    public List<SimulationCoordinate> resolveSpreadTargetCoordinates(
            int centerVerticalRowCoordinate,
            int centerHorizontalColumnCoordinate,
            int propagationSpreadRadius,
            String spatialPropagationGeometryType,
            int gridVerticalRowCount,
            int gridHorizontalColumnCount) {

        validateSpreadEngineArguments(
            centerVerticalRowCoordinate,
            centerHorizontalColumnCoordinate,
            propagationSpreadRadius,
            gridVerticalRowCount,
            gridHorizontalColumnCount);

        return switch (spatialPropagationGeometryType) {
            case String geometryType when geometryType.equalsIgnoreCase(VON_NEUMANN_SPATIAL_PROPAGATION_GEOMETRY) ->
                resolveVonNeumannSpreadTargetCoordinates(
                    centerVerticalRowCoordinate,
                    centerHorizontalColumnCoordinate,
                    propagationSpreadRadius,
                    gridVerticalRowCount,
                    gridHorizontalColumnCount);
            case String geometryType when geometryType.equalsIgnoreCase(MOORE_SPATIAL_PROPAGATION_GEOMETRY) ->
                resolveMooreSpreadTargetCoordinates(
                    centerVerticalRowCoordinate,
                    centerHorizontalColumnCoordinate,
                    propagationSpreadRadius,
                    gridVerticalRowCount,
                    gridHorizontalColumnCount);
            case String geometryType when geometryType.equalsIgnoreCase(ROW_SPATIAL_PROPAGATION_GEOMETRY) ->
                resolveRowSpreadTargetCoordinates(
                    centerVerticalRowCoordinate,
                    centerHorizontalColumnCoordinate,
                    propagationSpreadRadius,
                    gridVerticalRowCount,
                    gridHorizontalColumnCount);
            case String geometryType when geometryType.equalsIgnoreCase(COLUMN_SPATIAL_PROPAGATION_GEOMETRY) ->
                resolveColumnSpreadTargetCoordinates(
                    centerVerticalRowCoordinate,
                    centerHorizontalColumnCoordinate,
                    propagationSpreadRadius,
                    gridVerticalRowCount,
                    gridHorizontalColumnCount);
            case String geometryType when geometryType.equalsIgnoreCase(CROSS_HATCH_SPATIAL_PROPAGATION_GEOMETRY) ->
                resolveCrossHatchSpreadTargetCoordinates(
                    centerVerticalRowCoordinate,
                    centerHorizontalColumnCoordinate,
                    propagationSpreadRadius,
                    gridVerticalRowCount,
                    gridHorizontalColumnCount);
            default ->
                throw new IllegalArgumentException(
                    "Unsupported spatial propagation geometry type ["
                        + spatialPropagationGeometryType
                        + "]; supported geometries are: VonNeumann, Moore, Row, Column, CrossHatch.");
        };
    }

    private List<SimulationCoordinate> resolveVonNeumannSpreadTargetCoordinates(
            int centerVerticalRowCoordinate,
            int centerHorizontalColumnCoordinate,
            int propagationSpreadRadius,
            int gridVerticalRowCount,
            int gridHorizontalColumnCount) {

        List<SimulationCoordinate> listOfValidTargetCoordinates = new ArrayList<>();
        for (int verticalRowOffset = -propagationSpreadRadius; verticalRowOffset <= propagationSpreadRadius; verticalRowOffset++) {
            for (int horizontalColumnOffset = -propagationSpreadRadius; horizontalColumnOffset <= propagationSpreadRadius; horizontalColumnOffset++) {
                if (isWithinVonNeumannManhattanDistance(verticalRowOffset, horizontalColumnOffset, propagationSpreadRadius)
                    && isNotTheCenterCoordinate(verticalRowOffset, horizontalColumnOffset)) {
                    addInBoundsTargetCoordinate(
                        centerVerticalRowCoordinate + verticalRowOffset,
                        centerHorizontalColumnCoordinate + horizontalColumnOffset,
                        gridVerticalRowCount,
                        gridHorizontalColumnCount,
                        listOfValidTargetCoordinates);
                }
            }
        }
        return listOfValidTargetCoordinates;
    }

    private List<SimulationCoordinate> resolveMooreSpreadTargetCoordinates(
            int centerVerticalRowCoordinate,
            int centerHorizontalColumnCoordinate,
            int propagationSpreadRadius,
            int gridVerticalRowCount,
            int gridHorizontalColumnCount) {

        List<SimulationCoordinate> listOfValidTargetCoordinates = new ArrayList<>();
        for (int verticalRowOffset = -propagationSpreadRadius; verticalRowOffset <= propagationSpreadRadius; verticalRowOffset++) {
            for (int horizontalColumnOffset = -propagationSpreadRadius; horizontalColumnOffset <= propagationSpreadRadius; horizontalColumnOffset++) {
                if (isWithinMooreChebyshevDistance(verticalRowOffset, horizontalColumnOffset, propagationSpreadRadius)
                    && isNotTheCenterCoordinate(verticalRowOffset, horizontalColumnOffset)) {
                    addInBoundsTargetCoordinate(
                        centerVerticalRowCoordinate + verticalRowOffset,
                        centerHorizontalColumnCoordinate + horizontalColumnOffset,
                        gridVerticalRowCount,
                        gridHorizontalColumnCount,
                        listOfValidTargetCoordinates);
                }
            }
        }
        return listOfValidTargetCoordinates;
    }

    private List<SimulationCoordinate> resolveRowSpreadTargetCoordinates(
            int centerVerticalRowCoordinate,
            int centerHorizontalColumnCoordinate,
            int propagationSpreadRadius,
            int gridVerticalRowCount,
            int gridHorizontalColumnCount) {

        List<SimulationCoordinate> listOfValidTargetCoordinates = new ArrayList<>();
        for (int horizontalColumnOffset = -propagationSpreadRadius; horizontalColumnOffset <= propagationSpreadRadius; horizontalColumnOffset++) {
            if (horizontalColumnOffset != 0) {
                addInBoundsTargetCoordinate(
                    centerVerticalRowCoordinate,
                    centerHorizontalColumnCoordinate + horizontalColumnOffset,
                    gridVerticalRowCount,
                    gridHorizontalColumnCount,
                    listOfValidTargetCoordinates);
            }
        }
        return listOfValidTargetCoordinates;
    }

    private List<SimulationCoordinate> resolveColumnSpreadTargetCoordinates(
            int centerVerticalRowCoordinate,
            int centerHorizontalColumnCoordinate,
            int propagationSpreadRadius,
            int gridVerticalRowCount,
            int gridHorizontalColumnCount) {

        List<SimulationCoordinate> listOfValidTargetCoordinates = new ArrayList<>();
        for (int verticalRowOffset = -propagationSpreadRadius; verticalRowOffset <= propagationSpreadRadius; verticalRowOffset++) {
            if (verticalRowOffset != 0) {
                addInBoundsTargetCoordinate(
                    centerVerticalRowCoordinate + verticalRowOffset,
                    centerHorizontalColumnCoordinate,
                    gridVerticalRowCount,
                    gridHorizontalColumnCount,
                    listOfValidTargetCoordinates);
            }
        }
        return listOfValidTargetCoordinates;
    }

    private List<SimulationCoordinate> resolveCrossHatchSpreadTargetCoordinates(
            int centerVerticalRowCoordinate,
            int centerHorizontalColumnCoordinate,
            int propagationSpreadRadius,
            int gridVerticalRowCount,
            int gridHorizontalColumnCount) {

        List<SimulationCoordinate> listOfValidTargetCoordinates = new ArrayList<>();
        for (int verticalRowOffset = -propagationSpreadRadius; verticalRowOffset <= propagationSpreadRadius; verticalRowOffset++) {
            for (int horizontalColumnOffset = -propagationSpreadRadius; horizontalColumnOffset <= propagationSpreadRadius; horizontalColumnOffset++) {
                if (isDiagonalOffsetWithinCrossHatch(verticalRowOffset, horizontalColumnOffset)) {
                    addInBoundsTargetCoordinate(
                        centerVerticalRowCoordinate + verticalRowOffset,
                        centerHorizontalColumnCoordinate + horizontalColumnOffset,
                        gridVerticalRowCount,
                        gridHorizontalColumnCount,
                        listOfValidTargetCoordinates);
                }
            }
        }
        return listOfValidTargetCoordinates;
    }

    private boolean isWithinVonNeumannManhattanDistance(int verticalRowOffset, int horizontalColumnOffset, int propagationSpreadRadius) {
        return Math.abs(verticalRowOffset) + Math.abs(horizontalColumnOffset) <= propagationSpreadRadius;
    }

    private boolean isWithinMooreChebyshevDistance(int verticalRowOffset, int horizontalColumnOffset, int propagationSpreadRadius) {
        return Math.abs(verticalRowOffset) <= propagationSpreadRadius
            && Math.abs(horizontalColumnOffset) <= propagationSpreadRadius;
    }

    private boolean isDiagonalOffsetWithinCrossHatch(int verticalRowOffset, int horizontalColumnOffset) {
        return isNotTheCenterCoordinate(verticalRowOffset, horizontalColumnOffset)
            && Math.abs(verticalRowOffset) == Math.abs(horizontalColumnOffset);
    }

    private boolean isNotTheCenterCoordinate(int verticalRowOffset, int horizontalColumnOffset) {
        return verticalRowOffset != 0 || horizontalColumnOffset != 0;
    }

    private void addInBoundsTargetCoordinate(
            int targetVerticalRowCoordinate,
            int targetHorizontalColumnCoordinate,
            int gridVerticalRowCount,
            int gridHorizontalColumnCount,
            List<SimulationCoordinate> listOfValidTargetCoordinates) {

        if (targetVerticalRowCoordinate >= 0
            && targetVerticalRowCoordinate < gridVerticalRowCount
            && targetHorizontalColumnCoordinate >= 0
            && targetHorizontalColumnCoordinate < gridHorizontalColumnCount) {
            listOfValidTargetCoordinates.add(
                new SimulationCoordinate(targetVerticalRowCoordinate, targetHorizontalColumnCoordinate));
        }
    }

    private void validateSpreadEngineArguments(
            int centerVerticalRowCoordinate,
            int centerHorizontalColumnCoordinate,
            int propagationSpreadRadius,
            int gridVerticalRowCount,
            int gridHorizontalColumnCount) {

        if (gridVerticalRowCount <= 0) {
            throw new IllegalArgumentException(
                "Grid vertical row count must be positive; received [" + gridVerticalRowCount + "].");
        }
        if (gridHorizontalColumnCount <= 0) {
            throw new IllegalArgumentException(
                "Grid horizontal column count must be positive; received [" + gridHorizontalColumnCount + "].");
        }
        if (centerVerticalRowCoordinate < 0 || centerVerticalRowCoordinate >= gridVerticalRowCount) {
            throw new IllegalArgumentException(
                "Center vertical row coordinate [" + centerVerticalRowCoordinate
                    + "] is outside the grid row bounds [0, " + (gridVerticalRowCount - 1) + "].");
        }
        if (centerHorizontalColumnCoordinate < 0 || centerHorizontalColumnCoordinate >= gridHorizontalColumnCount) {
            throw new IllegalArgumentException(
                "Center horizontal column coordinate [" + centerHorizontalColumnCoordinate
                    + "] is outside the grid column bounds [0, " + (gridHorizontalColumnCount - 1) + "].");
        }
        if (propagationSpreadRadius < 0) {
            throw new IllegalArgumentException(
                "Propagation spread radius cannot be negative; received [" + propagationSpreadRadius + "].");
        }
    }
}