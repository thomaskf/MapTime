# MapTime — `AnchoredMap` User Guide

`AnchoredMap` loads one gen3sis paleo-environmental raster layer from a
NetCDF-4 (`.nc`) file and lets you query its values and summary statistics.
**One `AnchoredMap` object = one `.nc` file** (one variable, at one age).

- Source class: `src/main/java/maps/AnchoredMap.java`
- Package/import: `import maps.AnchoredMap;` (only needed when calling from another package)

---

## 1. Key concepts (read this first)

- **The grid.** Each file is a global grid of `1800` latitudes × `3600`
  longitudes at `0.1°` spacing. The value of interest is stored in the
  file's `Band1` variable.
- **Coordinates are signed decimal degrees.**
  - Latitude (`lat`): **North is +, South is −** (e.g. Sydney ≈ `-33.87`).
  - Longitude (`lon`): **East is +, West is −** (e.g. Sydney ≈ `151.21`).
  - Convert any degrees-minutes-seconds source first: `deg + min/60 + sec/3600`.
- **Axis order is ascending.** `lat[0] ≈ -89.95` (far south),
  `lat[1799] ≈ 89.95` (far north); `lon[0] ≈ -179.95`, `lon[3599] ≈ 179.95`.
- **No-data is `NaN`.** Cells with no value hold `Float.NaN`. The statistics
  methods skip them automatically; if you read raw values yourself, test with
  `Float.isNaN(v)`.
- **Loaded fully into memory.** The whole grid (~26 MB per file) is read on
  `load(...)`, so every query afterwards is fast in-memory array access.

---

## 2. Loading a file

```java
import maps.AnchoredMap;
import java.nio.file.Path;

AnchoredMap map = AnchoredMap.load(
        Path.of("../maps/map_anchored/map0_10.00Ma.nc"));
```

| Method | Returns | Notes |
|---|---|---|
| `static AnchoredMap load(Path file)` | `AnchoredMap` | Opens and reads the file. Throws `IOException` if the file is missing, has no `Band1`, or isn't a 2-D grid. The file handle is closed automatically before the method returns. |

`load` also parses the filename (`map0_10.00Ma.nc`) to fill in the `layer` and
`ageMa` fields below. If the name doesn't match the expected pattern, `layer`
becomes `"unknown"` and `ageMa` becomes `NaN` (no error).

---

## 3. Inspecting metadata (public fields)

After loading, read these directly (they are read-only `final` fields):

| Field | Type | Example | Meaning |
|---|---|---|---|
| `map.layer` | `String` | `"map"` | Variable code from the filename (`map`, `mat`, `z`, …). |
| `map.ageMa` | `double` | `10.0` | Reconstruction age, millions of years. |
| `map.path` | `String` | `/…/map0_10.00Ma.nc` | Absolute source path. |
| `map.nLat` | `int` | `1800` | Number of latitude rows. |
| `map.nLon` | `int` | `3600` | Number of longitude columns. |
| `map.lat` | `double[]` | `[-89.95 … 89.95]` | Latitude of each row (degrees north). |
| `map.lon` | `double[]` | `[-179.95 … 179.95]` | Longitude of each column (degrees east). |
| `map.globalAttributes` | `Map<String,String>` | `{Conventions=CF-1.5, …}` | File metadata, in file order. |

```java
System.out.println(map.layer + " at " + map.ageMa + " Ma");
System.out.println("grid: " + map.nLat + " x " + map.nLon);
System.out.println("lat range: " + map.lat[0] + " .. " + map.lat[map.nLat - 1]);
map.globalAttributes.forEach((k, v) -> System.out.println(k + " = " + v));
```

---

## 4. Reading values

### 4.1 By geographic coordinate (most common)

```java
float precip = map.valueAt(-33.87, 151.21);   // nearest cell to Sydney
if (Float.isNaN(precip)) {
    System.out.println("no data at that location");
} else {
    System.out.println("value = " + precip);
}
```

| Method | Returns | Notes |
|---|---|---|
| `float valueAt(double latDeg, double lonDeg)` | value at the **nearest** grid cell, or `NaN` if no data | Coordinates are signed decimal degrees. Out-of-range coordinates snap to the nearest edge cell (no exception). |

### 4.2 By grid index

```java
int row = map.latIndexOf(-33.87);   // latitude  -> row index
int col = map.lonIndexOf(151.21);   // longitude -> column index
float v = map.at(row, col);         // value at those indices
```

| Method | Returns | Notes |
|---|---|---|
| `float at(int latIndex, int lonIndex)` | value at exact indices, or `NaN` | **No bounds checking** — indices must be in `0..nLat-1` / `0..nLon-1`, or you get an `ArrayIndexOutOfBoundsException`. |
| `int latIndexOf(double latDeg)` | nearest row index | Clamps to `0 … nLat-1`. |
| `int lonIndexOf(double lonDeg)` | nearest column index | Clamps to `0 … nLon-1`. |

### 4.3 The whole grid at once

```java
float[][] g = map.grid();           // g[latIndex][lonIndex]
float corner = g[0][0];             // far south-west cell
```

| Method | Returns | Notes |
|---|---|---|
| `float[][] grid()` | the backing 2-D array `[lat][lon]` | For bulk work (export to CSV, custom math). **Do not modify** — it is the live internal array, not a copy. |

---

## 5. Summary statistics

All statistics ignore `NaN` cells and are returned as a `Stats` record
(see §6).

```java
AnchoredMap.Stats all = map.stats();
System.out.println(all);            // valid=… missing=… min=… max=… mean=…

// Australia, by geographic bounding box (degrees):
AnchoredMap.Stats oz = map.statsOfBox(-43.633, -10.683, 113.150, 153.633);
System.out.println("Australia mean = " + oz.mean());

// Or by raw index window:
AnchoredMap.Stats region = map.statsOfRegion(0, 899, 0, 1799); // SW quadrant
```

| Method | Returns | Notes |
|---|---|---|
| `Stats stats()` | statistics over the **whole** grid | Convenience for the full extent. |
| `Stats statsOfBox(double latMin, double latMax, double lonMin, double lonMax)` | statistics over a **geographic** box (inclusive, degrees) | Corner order doesn't matter — it sorts internally. |
| `Stats statsOfRegion(int lat0, int lat1, int lon0, int lon1)` | statistics over an **index** window (inclusive) | Lower-level; useful if you already have indices. |

> **Note:** a bounding box is a rectangle and includes ocean cells. In these
> files precipitation/temperature are defined over the ocean too, so a box
> mean is *not* a land-only mean. Mask with the elevation layer if you need
> land only.

---

## 6. The `Stats` record

`statsOfRegion`, `statsOfBox`, and `stats()` all return an
`AnchoredMap.Stats`, an immutable holder with these accessors:

| Accessor | Type | Meaning |
|---|---|---|
| `valid()` | `long` | number of non-`NaN` cells counted |
| `missing()` | `long` | number of `NaN` cells skipped |
| `min()` | `double` | minimum valid value (`NaN` if no valid cells) |
| `max()` | `double` | maximum valid value (`NaN` if no valid cells) |
| `mean()` | `double` | arithmetic mean of valid values (`NaN` if none) |

```java
AnchoredMap.Stats s = map.stats();
System.out.printf("%d cells, mean %.2f (%.2f–%.2f)%n",
        s.valid(), s.mean(), s.min(), s.max());
```

Its `toString()` prints: `valid=… missing=… min=… max=… mean=…`.

---

## 7. Lifecycle / closing

`AnchoredMap` implements `AutoCloseable`, but the underlying file is already
closed at the end of `load(...)`. Calling `close()` does nothing and is
optional — there is no resource leak if you skip it. You may still use
try-with-resources for style:

```java
// optional; not required
try (AnchoredMap m = AnchoredMap.load(file)) {
    System.out.println(m.stats());
}
```

To free the ~26 MB grid, just let the `AnchoredMap` go out of scope (the
garbage collector reclaims it).

---

## 8. Complete example

```java
import maps.AnchoredMap;
import java.nio.file.Path;

public class Demo {
    public static void main(String[] args) throws Exception {
        AnchoredMap map = AnchoredMap.load(
                Path.of("../maps/map_anchored/map0_10.00Ma.nc"));

        // metadata
        System.out.println(map.layer + " @ " + map.ageMa + " Ma, "
                + map.nLat + "x" + map.nLon);

        // a point value
        float sydney = map.valueAt(-33.87, 151.21);
        System.out.println("Sydney value = " + sydney);

        // whole-grid and regional stats
        System.out.println("global: " + map.stats());
        System.out.println("Australia: "
                + map.statsOfBox(-43.633, -10.683, 113.150, 153.633));
    }
}
```

---

## 9. Quick reference

```
load(Path)                              -> AnchoredMap          (static; throws IOException)
valueAt(latDeg, lonDeg)                 -> float   (NaN if none; nearest cell)
at(latIndex, lonIndex)                  -> float   (no bounds check)
latIndexOf(latDeg) / lonIndexOf(lonDeg) -> int     (clamped to grid)
grid()                                  -> float[][]  [lat][lon]  (do not modify)
stats()                                 -> Stats   (whole grid)
statsOfBox(latMin,latMax,lonMin,lonMax) -> Stats   (degrees, inclusive)
statsOfRegion(lat0,lat1,lon0,lon1)      -> Stats   (indices, inclusive)
fields: layer, ageMa, path, lat[], lon[], nLat, nLon, globalAttributes
Stats:  valid(), missing(), min(), max(), mean()
```
