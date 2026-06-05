package maps;

import java.nio.file.Path;

/**
 * Command-line demo: load one MapTime NetCDF file and print its
 * structure, global statistics, an Australia-region summary, and a couple of
 * point lookups. Useful as a smoke test and as a usage example.
 *
 * <pre>
 *   java -jar maptime.jar &lt;file.nc&gt;
 *   # default file if none given:
 *   java -jar maptime.jar
 * </pre>
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        Path file = (args.length > 0)
                ? Path.of(args[0])
                : Path.of("..", "maps", "map_anchored", "map0_10.00Ma.nc");

        System.out.println("Loading: " + file);
        AnchoredMap map = AnchoredMap.load(file);

        System.out.println();
        System.out.println("Layer   : " + map.layer);
        System.out.println("Age (Ma): " + map.ageMa);
        System.out.printf ("Grid    : %d lat x %d lon%n", map.nLat, map.nLon);
        System.out.printf ("Lat     : %.4f .. %.4f (step %.4f)%n",
                map.lat[0], map.lat[map.nLat - 1], map.lat[1] - map.lat[0]);
        System.out.printf ("Lon     : %.4f .. %.4f (step %.4f)%n",
                map.lon[0], map.lon[map.nLon - 1], map.lon[1] - map.lon[0]);

        System.out.println();
        System.out.println("Global attributes:");
        map.globalAttributes.forEach((k, v) -> {
            String shown = v.length() > 90 ? v.substring(0, 90) + "..." : v;
            System.out.println("  " + k + " = " + shown);
        });

        System.out.println();
        System.out.println("Global stats : " + map.stats());

        // Australia bounding box, in signed decimal degrees (the file convention:
        // lat = degrees_north so South is negative; lon = degrees_east).
        // Source extent in degrees-minutes:
        //   latitude  10°41'S .. 43°38'S  ->  -10.683 .. -43.633
        //   longitude 113°09'E .. 153°38'E -> 113.150 .. 153.633
        double latMin = -(43 + 38.0 / 60); // southern edge
        double latMax = -(10 + 41.0 / 60); // northern edge
        double lonMin =  113 + 9.0 / 60;   // western edge
        double lonMax =  153 + 38.0 / 60;  // eastern edge
        AnchoredMap.Stats oz = map.statsOfBox(latMin, latMax, lonMin, lonMax);
        System.out.printf("Australia box [lat %.3f..%.3f, lon %.3f..%.3f]:%n  %s%n",
                latMin, latMax, lonMin, lonMax, oz);

        System.out.println();
        // Two sample point lookups (Sydney, central Pacific).
        System.out.printf("value @ (-33.87, 151.21) Sydney    = %.3f%n",
                map.valueAt(-33.87, 151.21));
        System.out.printf("value @ (  0.00,-140.00) mid-Pacific= %.3f%n",
                map.valueAt(0.0, -140.0));

        map.close();
    }
}
