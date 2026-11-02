import javax.swing.*;

/** Entry point. Run this file. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Input input = new Input();
            Game game = new Game(input);
            JFrame f = new JFrame("School Escape");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(new GamePanel(game, input));
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}
