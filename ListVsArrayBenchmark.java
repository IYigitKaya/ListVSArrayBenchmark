import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.SwingWrapper;
import org.knowm.xchart.style.markers.None;

import java.awt.BasicStroke;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ListVsArrayBenchmark {

    static volatile int sink = 0;
    static final int[] SIZES = { 10_000, 20_000, 40_000, 80_000, 160_000 };
    static final int TRIALS_PER_SIZE = 10_000;
    static final int WARMUP_OPS = 50_000;
    static final long SEED = 7L;

    // ---------- Data structures ----------
    static class ResizingArrayInt {
        int[] a;
        int size;

        ResizingArrayInt(int cap) {
            a = new int[Math.max(1, cap)];
        }

        void append(int v) {
            ensure(size + 1);
            a[size++] = v;
        }

        void ensure(int n) {
            if (n <= a.length)
                return;
            int[] b = new int[Math.max(n, a.length * 2)];
            System.arraycopy(a, 0, b, 0, size);
            a = b;
        }

        int get(int i) {
            return a[i];
        }

        void insertAt(int i, int v) {
            ensure(size + 1);
            System.arraycopy(a, i, a, i + 1, size - i);
            a[i] = v;
            size++;
        }

        int deleteAt(int i) {
            int out = a[i];
            System.arraycopy(a, i + 1, a, i, size - i - 1);
            size--;
            return out;
        }
    }

    static class SinglyLinkedListInt {
        static class Node {
            int v;
            Node next;

            Node(int v) {
                this.v = v;
            }
        }

        Node head;
        int size;

        void append(int v) {
            Node n = new Node(v);
            if (head == null) {
                head = n;
                size = 1;
                return;
            }
            Node c = head;
            while (c.next != null)
                c = c.next;
            c.next = n;
            size++;
        }

        int get(int i) {
            Node c = head;
            for (int k = 0; k < i; k++)
                c = c.next;
            return c.v;
        }

        void insertAt(int i, int v) {
            if (i == 0) {
                Node n = new Node(v);
                n.next = head;
                head = n;
                size++;
                return;
            }
            Node p = head;
            for (int k = 0; k < i - 1; k++)
                p = p.next;
            Node n = new Node(v);
            n.next = p.next;
            p.next = n;
            size++;
        }

        int deleteAt(int i) {
            if (i == 0) {
                int out = head.v;
                head = head.next;
                size--;
                return out;
            }
            Node p = head;
            for (int k = 0; k < i - 1; k++)
                p = p.next;
            int out = p.next.v;
            p.next = p.next.next;
            size--;
            return out;
        }
    }

    static class Stats {
        long sum, max;
        int n;

        void add(long ns) {
            sum += ns;
            if (ns > max)
                max = ns;
            n++;
        }

        double mean() {
            return n == 0 ? 0 : (double) sum / n;
        }
    }

    static void populate(ResizingArrayInt ra, int n) {
        for (int i = 0; i < n; i++)
            ra.append(i);
    }

    static void populate(SinglyLinkedListInt ll, int n) {
        for (int i = 0; i < n; i++)
            ll.append(i);
    }

    static void warmup() {
        ResizingArrayInt ra = new ResizingArrayInt(16);
        SinglyLinkedListInt ll = new SinglyLinkedListInt();
        populate(ra, 10_000);
        populate(ll, 10_000);
        Random rng = new Random(SEED);
        for (int t = 0; t < WARMUP_OPS; t++) {
            int i = rng.nextInt(ra.size);
            sink ^= ra.get(i);
            sink ^= ll.get(i);
            ra.insertAt(i, t);
            ra.deleteAt(i);
            ll.insertAt(i, t);
            ll.deleteAt(i);
            ra.append(t);
            ra.deleteAt(ra.size - 1);
            ll.append(t);
            ll.deleteAt(ll.size - 1);
            ra.insertAt(0, t);
            ra.deleteAt(0);
            ll.insertAt(0, t);
            ll.deleteAt(0);
        }
    }

    static void printProgress(int curr, int total) {
        int width = 40; // console bar width
        double pct = (total == 0) ? 1.0 : (double) curr / total;
        int filled = (int) Math.round(pct * width);

        StringBuilder bar = new StringBuilder();
        bar.append('\r').append('[');
        for (int i = 0; i < width; i++)
            bar.append(i < filled ? '=' : ' ');
        bar.append("] ").append(String.format("%3d%%", (int) (pct * 100)));

        System.out.print(bar);
        System.out.flush();
    }

    public static void main(String[] args) {
        warmup();

        String[] OPS = { "access", "insert", "delete", "append", "prepend" };
        Map<String, Map<Integer, double[]>> data = new LinkedHashMap<>();
        for (String op : OPS)
            data.put(op, new LinkedHashMap<>());

        for (int n : SIZES) {
            System.out.println("\nBenchmarking N=" + n);

            ResizingArrayInt ra = new ResizingArrayInt(n);
            populate(ra, n);
            SinglyLinkedListInt ll = new SinglyLinkedListInt();
            populate(ll, n);

            Random rng = new Random(SEED + n);
            Stats raA = new Stats(), llA = new Stats(); // access
            Stats raI = new Stats(), llI = new Stats(); // insert (random i)
            Stats raD = new Stats(), llD = new Stats(); // delete (random i)
            Stats raP = new Stats(), llP = new Stats(); // append (end)
            Stats raH = new Stats(), llH = new Stats(); // prepend (head)

            for (int t = 0; t < TRIALS_PER_SIZE; t++) {

                printProgress(t, TRIALS_PER_SIZE - 1);

                int i = rng.nextInt(n), v = rng.nextInt();
                long s, d;

                // ACCESS
                s = System.nanoTime();
                sink ^= ra.get(i);
                d = System.nanoTime() - s;
                raA.add(d);

                s = System.nanoTime();
                sink ^= ll.get(i);
                d = System.nanoTime() - s;
                llA.add(d);

                // INSERT (random position)
                s = System.nanoTime();
                ra.insertAt(i, v);
                d = System.nanoTime() - s;
                raI.add(d);
                ra.deleteAt(i);

                s = System.nanoTime();
                ll.insertAt(i, v);
                d = System.nanoTime() - s;
                llI.add(d);
                ll.deleteAt(i);

                // DELETE (random position)
                int tmp;
                s = System.nanoTime();
                tmp = ra.deleteAt(i);
                d = System.nanoTime() - s;
                raD.add(d);
                ra.insertAt(i, tmp);

                s = System.nanoTime();
                tmp = ll.deleteAt(i);
                d = System.nanoTime() - s;
                llD.add(d);
                ll.insertAt(i, tmp);

                // APPEND (end)
                s = System.nanoTime();
                ra.append(v);
                d = System.nanoTime() - s;
                raP.add(d);
                ra.deleteAt(ra.size - 1);

                s = System.nanoTime();
                ll.append(v);
                d = System.nanoTime() - s;
                llP.add(d);
                ll.deleteAt(ll.size - 1);

                // PREPEND (insert at head)
                s = System.nanoTime();
                ra.insertAt(0, v);
                d = System.nanoTime() - s;
                raH.add(d);
                ra.deleteAt(0);

                s = System.nanoTime();
                ll.insertAt(0, v);
                d = System.nanoTime() - s;
                llH.add(d);
                ll.deleteAt(0);
            }

            data.get("access").put(n, new double[] { raA.mean(), llA.mean(), raA.max, llA.max });
            data.get("insert").put(n, new double[] { raI.mean(), llI.mean(), raI.max, llI.max });
            data.get("delete").put(n, new double[] { raD.mean(), llD.mean(), raD.max, llD.max });
            data.get("append").put(n, new double[] { raP.mean(), llP.mean(), raP.max, llP.max });
            data.get("prepend").put(n, new double[] { raH.mean(), llH.mean(), raH.max, llH.max });
        }

        XYChart acc = makeChart("Access — Mean & Max (ns)", data.get("access"));
        XYChart ins = makeChart("Insert — Mean & Max (ns)", data.get("insert"));
        XYChart del = makeChart("Delete — Mean & Max (ns)", data.get("delete"));
        XYChart app = makeChart("Append — Mean & Max (ns)", data.get("append"));
        XYChart pre = makeChart("Prepend (Head Insert) — Mean & Max (ns)", data.get("prepend"));

        new SwingWrapper<>(Arrays.asList(acc, ins, del, app, pre))
                .setTitle("List vs Array Benchmark (Mean & Max)")
                .displayChartMatrix();
    }

    static XYChart makeChart(String title, Map<Integer, double[]> block) {
        XYChart chart = new XYChartBuilder().width(700).height(450)
                .title(title).xAxisTitle("Size (N)").yAxisTitle("Time (ns)").build();

        List<Integer> x = new ArrayList<>();
        for (int n : SIZES)
            x.add(n);
        double[] arrM = new double[SIZES.length], listM = new double[SIZES.length], arrX = new double[SIZES.length],
                listX = new double[SIZES.length];
        for (int i = 0; i < SIZES.length; i++) {
            double[] v = block.get(SIZES[i]);
            arrM[i] = v[0];
            listM[i] = v[1];
            arrX[i] = v[2];
            listX[i] = v[3];
        }

        // Array = blue, LinkedList = red | solid = mean, dashed = max
        Color arrColor = new Color(30, 144, 255);
        Color llColor = new Color(220, 20, 60);

        XYSeries arrMean = chart.addSeries("Array_mean", x, toList(arrM));
        arrMean.setMarker(new None());
        arrMean.setLineColor(arrColor);
        arrMean.setLineStyle(new BasicStroke(2.0f));

        XYSeries arrMax = chart.addSeries("Array_max", x, toList(arrX));
        arrMax.setMarker(new None());
        arrMax.setLineColor(arrColor);
        arrMax.setLineStyle(
                new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 6, 6 }, 0));

        XYSeries llMean = chart.addSeries("LinkedList_mean", x, toList(listM));
        llMean.setMarker(new None());
        llMean.setLineColor(llColor);
        llMean.setLineStyle(new BasicStroke(2.0f));

        XYSeries llMax = chart.addSeries("LinkedList_max", x, toList(listX));
        llMax.setMarker(new None());
        llMax.setLineColor(llColor);
        llMax.setLineStyle(
                new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 6, 6 }, 0));

        return chart;
    }

    static List<Double> toList(double[] a) {
        List<Double> o = new ArrayList<>(a.length);
        for (double v : a)
            o.add(v);
        return o;
    }
}
