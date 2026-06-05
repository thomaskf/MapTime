# MapTime — How to use `AnchoredMap`

`AnchoredMap` is the main class. It reads one map file (`.nc`) and lets you ask
for the numbers inside it.

**One `AnchoredMap` object = one file.** One file holds one kind of value, for
the whole world, at one age.

- Where the code is: `src/main/java/maps/AnchoredMap.java`
- To use it from another package, add: `import maps.AnchoredMap;`

---

## 1. Things to know first

- **The grid.** Each file is a grid of the whole world: 1800 rows (latitude) and
  3600 columns (longitude). Each cell is 0.1 degrees wide. The values are in a
  part called `Band1`.
- **Latitude and longitude are plain numbers (decimal degrees).**
  - Latitude: North is `+`, South is `-`. (Sydney is about `-33.87`.)
  - Longitude: East is `+`, West is `-`. (Sydney is about `151.21`.)
  - If your numbers look like `43°38'` (degrees and minutes), change them first:
    `degrees + minutes/60 + seconds/3600`.
- **The order of the grid.** `lat[0]` is the far south (`-89.95`). `lat[1799]` is
  the far north (`89.95`). It is the same idea for `lon`.
- **Empty cells are `NaN`.** Some cells have no value. They hold `NaN` ("not a
  number"). The stats methods skip these cells for you. If you read a value
  yourself, check it with `Float.isNaN(value)`.
- **The whole file is read into memory.** When you open a file, all of it is
  loaded (about 26 MB). After that, asking for values is very fast.

---

## 2. Open a file

```java
import maps.AnchoredMap;
import java.nio.file.Path;

AnchoredMap map = AnchoredMap.load(
        Path.of("../maps/map_anchored/map0_10.00Ma.nc"));
```

| Method | Gives you | Notes |
|---|---|---|
| `static AnchoredMap load(Path file)` | a new `AnchoredMap` | Opens and reads the file. Throws `IOException` if the file is missing, has no `Band1`, or is not a 2-D grid. The file is closed for you after reading. |

`load` also reads the name of the file (like `map0_10.00Ma.nc`) to fill in
`layer` and `ageMa` (see below). If the name does not fit the pattern, `layer`
becomes `"unknown"` and `ageMa` becomes `NaN`. It does not crash.

---

## 3. See the file's info (fields you can read)

After loading, you can read these directly. You can read them but not change
them.

| Field | Type | Example | Meaning |
|---|---|---|---|
| `map.layer` | `String` | `"map"` | The short code from the file name (`map`, `mat`, `z`, ...). |
| `map.ageMa` | `double` | `10.0` | The age, in millions of years. |
| `map.path` | `String` | `/.../map0_10.00Ma.nc` | The full file path. |
| `map.nLat` | `int` | `1800` | Number of rows (latitude). |
| `map.nLon` | `int` | `3600` | Number of columns (longitude). |
| `map.lat` | `double[]` | `[-89.95 ... 89.95]` | The latitude of each row. |
| `map.lon` | `double[]` | `[-179.95 ... 179.95]` | The longitude of each column. |
| `map.globalAttributes` | `Map<String,String>` | `{Conventions=CF-1.5, ...}` | Extra info from the file. |

```java
System.out.println(map.layer + " at " + map.ageMa + " Ma");
System.out.println("grid: " + map.nLat + " x " + map.nLon);
map.globalAttributes.forEach((k, v) -> System.out.println(k + " = " + v));
```

---

## 4. Get a value

### 4.1 By place (most common)

```java
float value = map.valueAt(-33.87, 151.21);   // near Sydney
if (Float.isNaN(value)) {
    System.out.println("no value here");
} else {
    System.out.println("value = " + value);
}
```

| Method | Gives you | Notes |
|---|---|---|
| `float valueAt(double latDeg, double lonDeg)` | the value at the nearest cell, or `NaN` if empty | Give it latitude and longitude as plain numbers. If the place is outside the map, it uses the nearest edge cell (no crash). |

### 4.2 By cell number (row and column)

```java
int row = map.latIndexOf(-33.87);   // latitude  -> row number
int col = map.lonIndexOf(151.21);   // longitude -> column number
float value = map.at(row, col);     // value at that cell
```

| Method | Gives you | Notes |
|---|---|---|
| `float at(int latIndex, int lonIndex)` | the value at that cell | **No safety check.** The numbers must be inside the grid, or you get an error. |
| `int latIndexOf(double latDeg)` | the nearest row number | Stays inside the grid. |
| `int lonIndexOf(double lonDeg)` | the nearest column number | Stays inside the grid. |

### 4.3 The whole grid at once

```java
float[][] g = map.grid();   // g[row][column]
float corner = g[0][0];     // far south-west cell
```

| Method | Gives you | Notes |
|---|---|---|
| `float[][] grid()` | the full 2-D table `[row][column]` | Use this for big jobs (like saving to a CSV file). **Do not change it.** It is the real table, not a copy. |

---

## 5. Get stats

All stats skip empty (`NaN`) cells. They give you back a `Stats` object (see
part 6).

```java
// Whole world:
AnchoredMap.Stats all = map.stats();
System.out.println(all);

// One box on the map (Australia), using latitude/longitude:
AnchoredMap.Stats oz = map.statsOfBox(-43.633, -10.683, 113.150, 153.633);
System.out.println("Australia average = " + oz.mean());

// One box, using cell numbers:
AnchoredMap.Stats part = map.statsOfRegion(0, 899, 0, 1799);
```

| Method | Gives you | Notes |
|---|---|---|
| `Stats stats()` | stats for the whole world | The easy one. |
| `Stats statsOfBox(double latMin, double latMax, double lonMin, double lonMax)` | stats for a box, using latitude/longitude | The order of the corners does not matter. |
| `Stats statsOfRegion(int lat0, int lat1, int lon0, int lon1)` | stats for a box, using cell numbers | Use this if you already have cell numbers. |

> **Note:** a box is a rectangle. Around Australia, the box also covers some
> ocean. In these files the ocean has values too, so a box average is **not**
> the land-only average. To get land only, you can remove ocean cells using the
> height layer (`z_anchored`, where the ocean is below 0).

---

## 6. The `Stats` object

`stats()`, `statsOfBox()`, and `statsOfRegion()` all give you a `Stats` object.
You read its parts like this:

| Part | Type | Meaning |
|---|---|---|
| `valid()` | `long` | how many cells had a value |
| `missing()` | `long` | how many cells were empty (`NaN`) |
| `min()` | `double` | the smallest value (`NaN` if none) |
| `max()` | `double` | the largest value (`NaN` if none) |
| `mean()` | `double` | the average value (`NaN` if none) |

```java
AnchoredMap.Stats s = map.stats();
System.out.printf("%d cells, average %.2f (from %.2f to %.2f)%n",
        s.valid(), s.mean(), s.min(), s.max());
```

If you print a `Stats` object, it shows:
`valid=... missing=... min=... max=... mean=...`

---

## 7. Closing the file

`AnchoredMap` can be used with try-with-resources, but you do not have to. The
file is already closed after `load(...)`. The `close()` method does nothing, so
nothing leaks if you skip it.

```java
// This works, but is not needed:
try (AnchoredMap m = AnchoredMap.load(file)) {
    System.out.println(m.stats());
}
```

To free the memory, just stop using the object. Java cleans it up for you.

---

## 8. Full example

```java
import maps.AnchoredMap;
import java.nio.file.Path;

public class Demo {
    public static void main(String[] args) throws Exception {
        AnchoredMap map = AnchoredMap.load(
                Path.of("../maps/map_anchored/map0_10.00Ma.nc"));

        // info
        System.out.println(map.layer + " at " + map.ageMa + " Ma, "
                + map.nLat + "x" + map.nLon);

        // one value
        float sydney = map.valueAt(-33.87, 151.21);
        System.out.println("Sydney value = " + sydney);

        // stats
        System.out.println("world: " + map.stats());
        System.out.println("Australia: "
                + map.statsOfBox(-43.633, -10.683, 113.150, 153.633));
    }
}
```

---

## 9. Quick list

```
load(Path)                              -> AnchoredMap   (can throw IOException)
valueAt(latDeg, lonDeg)                 -> float   (NaN if empty; nearest cell)
at(latIndex, lonIndex)                  -> float   (no safety check)
latIndexOf(latDeg) / lonIndexOf(lonDeg) -> int     (stays inside the grid)
grid()                                  -> float[][]  [row][column]  (do not change)
stats()                                 -> Stats   (whole world)
statsOfBox(latMin,latMax,lonMin,lonMax) -> Stats   (box, lat/lon)
statsOfRegion(lat0,lat1,lon0,lon1)      -> Stats   (box, cell numbers)

fields: layer, ageMa, path, lat[], lon[], nLat, nLon, globalAttributes
Stats:  valid(), missing(), min(), max(), mean()
```
