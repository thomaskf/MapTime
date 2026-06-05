# MapTime

A small Java library for loading **paleo-environmental raster layers** stored as
NetCDF-4 (`.nc`) files, for analysis on the JVM. The data are global
deep-time reconstructions (e.g. for a [gen3sis](https://github.com/project-gen3sis)
landscape simulation of Australia) — one variable per file, at a series of
geological ages.

## What it does

Each `.nc` file holds a single global grid (1800 lat × 3600 lon at 0.1°) in a
`Band1` variable, with `lat`/`lon` coordinate axes and `NaN` for no-data.
The `AnchoredMap` class loads one such file and lets you:

- read metadata (layer name, age, grid size, axes, global attributes),
- look up values by geographic coordinate (`valueAt`) or grid index (`at`),
- compute summary statistics over the whole grid, an index window, or a
  geographic bounding box.

See **[USAGE.md](USAGE.md)** for the full API guide.

## Build

Requires JDK 21+ and Maven.

```bash
mvn package
```

This produces a runnable fat-jar at `target/maptime.jar`.

## Run the demo

```bash
java -jar target/maptime.jar [path/to/file.nc]
```

With no argument it loads `../maps/map_anchored/map0_10.00Ma.nc` (see Data below).

## Use as a library

```java
import maps.AnchoredMap;
import java.nio.file.Path;

AnchoredMap map = AnchoredMap.load(Path.of("path/to/map0_10.00Ma.nc"));
float v = map.valueAt(-33.87, 151.21);                  // nearest cell (Sydney)
AnchoredMap.Stats oz = map.statsOfBox(-43.633, -10.683, 113.150, 153.633);
System.out.println(oz);                                 // Australia summary
```

Coordinates are **signed decimal degrees** (North/East positive, South/West
negative).

## Data

The NetCDF data files (~2 GB total) are **not stored in this repository** —
they are large static binaries unsuitable for git. Place them alongside the
project so the demo's default path resolves:

```
<parent>/
├── maps/            # data: ed_anchored/, map_anchored/, mat_anchored/, ...
└── maptime/         # this repository
```

<!-- TODO: add the public download link (e.g. Zenodo/Figshare DOI) for the data here. -->

## Dependencies

- [netCDF-Java (CDM)](https://www.unidata.ucar.edu/software/netcdf-java/)
  `edu.ucar:cdm-core` — pure-Java NetCDF-4/HDF5 reader (no native libraries).
- SLF4J (`slf4j-api` + `slf4j-simple`) for logging.

Maven resolves these automatically.
