# ListVSArrayBenchmark
This project provides a comprehensive performance analysis between two fundamental data structures: Dynamic Arrays and Singly Linked Lists. By benchmarking core operations across increasing data sizes, it visualizes the practical trade-offs between contiguous memory and pointer-based structures.

Benchmarked Operations
The suite evaluates the following operations implemented from scratch:

Access: Random access by index.

Insert/Delete: Modifications at random positions.

Append: Adding elements to the end of the structure.

Prepend: Inserting elements at the head (index 0).

Technical Implementation
To ensure high-fidelity measurements, the benchmark incorporates several low-level JVM strategies:

JIT Warmup: A dedicated warmup() phase executes 50,000 operations to allow the Just-In-Time (JIT) compiler to optimize the bytecode before actual measurements begin.

Dead Code Elimination Prevention: A volatile int sink is used to consume operation results, preventing the compiler from optimizing away "unused" return values during stress tests.

High-Resolution Timing: Uses System.nanoTime() to capture latency in nanoseconds.

Statistical Analysis: Tracks both Mean and Maximum latency to identify performance spikes and outliers across 10,000 trials per size.

Visualization
The results are plotted using the XChart library. The program generates a matrix of charts comparing:

Array Performance (Solid/Dashed Blue lines).

LinkedList Performance (Solid/Dashed Red lines).

Data sizes ranging from 10,000 to 160,000 elements.
<img width="1903" height="982" alt="ListVSarray" src="https://github.com/user-attachments/assets/a2426814-5afc-42de-a019-678b9eab90b5" />
