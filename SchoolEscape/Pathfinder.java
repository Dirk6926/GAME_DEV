/**
 * Breadth-first search on the grid. Uses a Queue for the frontier and a Stack to reverse the path,
 * then returns the waypoints as a Queue that the Stalker follows.
 */
public final class Pathfinder {
    private Pathfinder() {}

    public static MyQueue<double[]> findPath(World w, int sx, int sy, int tx, int ty) {
        int width = World.W, height = World.H;
        int[] parent = new int[width * height];
        boolean[] seen = new boolean[width * height];
        MyQueue<Integer> frontier = new MyQueue<>();
        int start = sy * width + sx, goal = ty * width + tx;
        MyQueue<double[]> out = new MyQueue<>();
        if (sx < 0 || sy < 0 || sx >= width || sy >= height || tx < 0 || ty < 0 || tx >= width || ty >= height) return out;
        frontier.enqueue(start);
        seen[start] = true;
        parent[start] = -1;
        int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
        boolean found = false;
        while (!frontier.isEmpty()) {
            int cur = frontier.dequeue();
            if (cur == goal) { found = true; break; }
            int cx = cur % width, cy = cur / width;
            for (int i = 0; i < 4; i++) {
                int nx = cx + dx[i], ny = cy + dy[i];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                if (w.isSolid(nx, ny)) continue;
                int n = ny * width + nx;
                if (seen[n]) continue;
                seen[n] = true;
                parent[n] = cur;
                frontier.enqueue(n);
            }
        }
        if (!found) return out;
        MyStack<Integer> reversed = new MyStack<>();
        for (int c = goal; c != -1; c = parent[c]) reversed.push(c);
        reversed.pop();
        while (!reversed.isEmpty()) {
            int c = reversed.pop();
            out.enqueue(new double[]{c % width + 0.5, c / width + 0.5});
        }
        return out;
    }
}
