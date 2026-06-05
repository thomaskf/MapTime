# MapTime

MapTime is a small Java tool. It reads map data files and gives you the numbers
inside them.

The files are `.nc` files (NetCDF format). Each file is one map of the whole
world at one moment in deep time (millions of years ago). One file holds one
kind of value, for example rainfall or temperature.

## What one file holds

- A grid of the whole world: 1800 rows (latitude) and 3600 columns (longitude).
- Each cell is 0.1 degrees wide.
- The values are stored in a part called `Band1`.
- Cells with no value are marked `NaN` ("not a number").

## What MapTime can do

The main class is called `AnchoredMap`. After it reads a file, you can:

- See the file's info (its name, its age, the grid size).
- Get the value at a place, using latitude and longitude.
- Get simple stats (smallest, largest, and average value) for the whole world,
  or for one box on the map.

For all the details, see **[USAGE.md](USAGE.md)**.

## What you need

- Java 21 or newer.
- Maven (a tool that builds Java projects).

## How to build it

Open a terminal in this folder and run:

```bash
mvn package
```

This makes one ready-to-run file: `target/maptime.jar`.

## How to run the demo

```bash
java -jar target/maptime.jar [path to a .nc file]
```

If you give no path, it uses `../maps/map_anchored/map0_10.00Ma.nc` (see "Where
the data is" below).

The demo prints these results:

```text
Global stats : valid=6472587 missing=7413 min=6.6530 max=5109.9824 mean=912.2941
Australia box [lat -43.633..-10.683, lon 113.150..153.633]:
  valid=134386 missing=0 min=253.0417 max=2025.8651 mean=744.8467

value @ (-33.87, 151.21) Sydney    = 569.507
value @ (  0.00,-140.00) mid-Pacific= 570.827
```

What the numbers mean (this file holds rainfall, in mm per year):

- The world has 6,472,587 cells with a value, and 7,413 empty cells.
- World rainfall goes from about 7 to 5110 mm. The average is about 912 mm.
- In the Australia box, the average is about 745 mm.
- Near Sydney, the value is about 570 mm.

## How to use it in your own code

```java
import maps.AnchoredMap;
import java.nio.file.Path;

// 1. Open a file.
AnchoredMap map = AnchoredMap.load(Path.of("path/to/map0_10.00Ma.nc"));

// 2. Get the value near Sydney.
float value = map.valueAt(-33.87, 151.21);

// 3. Get stats for the Australia box.
AnchoredMap.Stats oz = map.statsOfBox(-43.633, -10.683, 113.150, 153.633);
System.out.println(oz);
```

This prints:

```text
value (near Sydney) = 569.507
valid=134386 missing=0 min=253.0417 max=2025.8651 mean=744.8467
```

Latitude and longitude use plain numbers (decimal degrees):

- North is `+`, South is `-`.
- East is `+`, West is `-`.

## Where the data is

The data files are very big (about 2 GB in total). They are **not** in this
repository, because GitHub is not made for big data files.

Keep the data next to this project, like this:

```
<parent folder>/
├── maps/            (the data: map_anchored, mat_anchored, z_anchored, ...)
└── maptime/         (this project)
```

<!-- TODO: add the public download link for the data here. -->

## Libraries it uses

- **netCDF-Java (CDM)** — reads the `.nc` files. It is pure Java, so you do not
  need to install anything extra.
- **SLF4J** — used for log messages.

Maven downloads these for you.
