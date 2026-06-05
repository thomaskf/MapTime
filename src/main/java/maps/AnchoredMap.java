package maps;

import ucar.ma2.Array;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFiles;
import ucar.nc2.Variable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One gen3sis paleo-environmental raster layer, loaded from a NetCDF-4 file.
 *
 * <p>Each file holds a single global 0.1&deg; grid in the {@code Band1}
 * variable, with {@code lat} (1800) and {@code lon} (3600) coordinate axes.
 * No-data cells are stored as {@code NaN}.
 *
 * <p>The whole {@code Band1} grid (~26&nbsp;MB) is read into memory on load so
 * that subsequent lookups and stats are pure array access.
 */
public final class AnchoredMap implements AutoCloseable {

    /** e.g. {@code "map"}, {@code "mat"}, {@code "z"} – parsed from the filename. */
    public final String layer;
    /** Reconstruction age in millions of years, parsed from the filename (e.g. 10.0). */
    public final double ageMa;
    /** Absolute source path. */
    public final String path;

    /** Latitude axis (degrees north), length {@link #nLat}; ascending. */
    public final double[] lat;
    /** Longitude axis (degrees east), length {@link #nLon}; ascending. */
    public final double[] lon;
    public final int nLat;
    public final int nLon;

    /** Grid values indexed as {@code data[latIndex][lonIndex]}; {@code NaN} = no data. */
    private final float[][] data;

    /** Global attributes, in file order (handy for provenance / the {@code history} string). */
    public final Map<String, String> globalAttributes;

    private AnchoredMap(String layer, double ageMa, String path,
                        double[] lat, double[] lon, float[][] data,
                        Map<String, String> globalAttributes) {
        this.layer = layer;
        this.ageMa = ageMa;
        this.path = path;
        this.lat = lat;
        this.lon = lon;
        this.nLat = lat.length;
        this.nLon = lon.length;
        this.data = data;
        this.globalAttributes = globalAttributes;
    }

    // Matches names like "map0_10.00Ma.nc", "z0_0.00Ma.nc", "tse0_80.00Ma.nc".
    private static final Pattern NAME = Pattern.compile(
            "^([a-zA-Z]+)\\d*_([0-9]+(?:\\.[0-9]+)?)Ma\\.nc$");

    /** Open a single {@code .nc} layer file and read it fully into memory. */
    public static AnchoredMap load(Path file) throws IOException {
        String fname = file.getFileName().toString();
        String layer = "unknown";
        double ageMa = Double.NaN;
        Matcher m = NAME.matcher(fname);
        if (m.matches()) {
            layer = m.group(1);
            ageMa = Double.parseDouble(m.group(2));
        }

        try (NetcdfFile nc = NetcdfFiles.open(file.toAbsolutePath().toString())) {
            double[] lat = readDoubleAxis(nc, "lat");
            double[] lon = readDoubleAxis(nc, "lon");

            Variable band = nc.findVariable("Band1");
            if (band == null) {
                throw new IOException("No 'Band1' variable in " + fname);
            }
            int[] shape = band.getShape();
            if (shape.length != 2) {
                throw new IOException("Expected 2-D Band1, got rank " + shape.length);
            }
            int rows = shape[0]; // lat
            int cols = shape[1]; // lon

            Array arr = band.read();                 // float, possibly NaN-filled
            float[][] data = new float[rows][cols];
            int k = 0;
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    data[i][j] = arr.getFloat(k++);
                }
            }

            Map<String, String> gattrs = new LinkedHashMap<>();
            for (Attribute a : nc.getRootGroup().attributes()) {
                gattrs.put(a.getName(), a.getStringValue() != null
                        ? a.getStringValue() : a.getValues().toString());
            }

            return new AnchoredMap(layer, ageMa, file.toAbsolutePath().toString(),
                    lat, lon, data, gattrs);
        }
    }

    private static double[] readDoubleAxis(NetcdfFile nc, String name) throws IOException {
        Variable v = nc.findVariable(name);
        if (v == null) throw new IOException("Missing coordinate variable '" + name + "'");
        Array a = v.read();
        int n = (int) a.getSize();
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = a.getDouble(i);
        return out;
    }

    // ---- access -----------------------------------------------------------

    /** Raw value at grid indices; {@code NaN} if no data. No bounds clamping. */
    public float at(int latIndex, int lonIndex) {
        return data[latIndex][lonIndex];
    }

    /** Nearest-cell value for a geographic coordinate; {@code NaN} if no data. */
    public float valueAt(double latDeg, double lonDeg) {
        return data[latIndexOf(latDeg)][lonIndexOf(lonDeg)];
    }

    /** Nearest latitude row for a degree value (assumes a regular ascending axis). */
    public int latIndexOf(double latDeg) {
        return nearest(lat, latDeg);
    }

    /** Nearest longitude column for a degree value (assumes a regular ascending axis). */
    public int lonIndexOf(double lonDeg) {
        return nearest(lon, lonDeg);
    }

    private static int nearest(double[] axis, double value) {
        double step = (axis[axis.length - 1] - axis[0]) / (axis.length - 1);
        int idx = (int) Math.round((value - axis[0]) / step);
        if (idx < 0) return 0;
        if (idx >= axis.length) return axis.length - 1;
        return idx;
    }

    // ---- statistics -------------------------------------------------------

    /** Summary statistics over all valid (non-NaN) cells. */
    public Stats stats() {
        return statsOfRegion(0, nLat - 1, 0, nLon - 1);
    }

    /** Summary statistics over an inclusive index window. */
    public Stats statsOfRegion(int lat0, int lat1, int lon0, int lon1) {
        long valid = 0, missing = 0;
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY, sum = 0;
        for (int i = lat0; i <= lat1; i++) {
            for (int j = lon0; j <= lon1; j++) {
                float v = data[i][j];
                if (Float.isNaN(v)) { missing++; continue; }
                valid++;
                if (v < min) min = v;
                if (v > max) max = v;
                sum += v;
            }
        }
        double mean = valid > 0 ? sum / valid : Double.NaN;
        if (valid == 0) { min = Double.NaN; max = Double.NaN; }
        return new Stats(valid, missing, min, max, mean);
    }

    /** Statistics over a geographic bounding box (degrees), inclusive. */
    public Stats statsOfBox(double latMin, double latMax, double lonMin, double lonMax) {
        int i0 = latIndexOf(latMin), i1 = latIndexOf(latMax);
        int j0 = lonIndexOf(lonMin), j1 = lonIndexOf(lonMax);
        return statsOfRegion(Math.min(i0, i1), Math.max(i0, i1),
                             Math.min(j0, j1), Math.max(j0, j1));
    }

    public Dimension latDim() { return new Dimension("lat", nLat); }

    /** Direct access to the backing grid; caller must not mutate. */
    public float[][] grid() { return data; }

    @Override
    public void close() { /* file already closed after load; here for try-with-resources symmetry */ }

    /** Immutable summary of a set of cells. */
    public record Stats(long valid, long missing, double min, double max, double mean) {
        @Override public String toString() {
            return String.format("valid=%d missing=%d min=%.4f max=%.4f mean=%.4f",
                    valid, missing, min, max, mean);
        }
    }
}
