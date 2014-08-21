import java.awt.*;

/**
 * Created by Peter Mösenthin.
 */
public class DisplayFrame extends javax.swing.JFrame {
    public DisplayFrame(){
        this.setSize(800, 600); //The window Dimensions
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        javax.swing.JPanel panel = new javax.swing.JPanel();
        panel.setBounds(0,0, 800, 600);
        panel.setBackground(new Color(150,150,150));
        processing.core.PApplet sketch = new FalldownApplet();
        panel.add(sketch);
        this.add(panel);
        sketch.init(); //this is the function used to start the execution of the sketch
        this.setVisible(true);
    }
}
