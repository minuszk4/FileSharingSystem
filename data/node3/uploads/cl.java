import java.io.*;
import java.net.Socket;
import TCP.Laptop;

public class cl {
    public static void main(String[] args) {
        String host = "203.162.10.109";
        int port = 2209;
        String studentCode = "B22DCCN246;eEUnSBzq";

        try (Socket socket = new Socket(host, port)) {

            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
            oos.writeObject(studentCode);
            oos.flush();
            Object obs =  ois.readObject();
            Laptop laptop = (Laptop) obs;


            laptop.name = fixName(laptop.name);
            laptop.quantity = fixQuantity(laptop.quantity);

            oos.writeObject(laptop);
            oos.flush();
            System.out.println("\nĐã gửi lại Laptop đã sửa.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String fixName(String name) {
        String[] parts = name.trim().split("\\s+");
        if (parts.length > 1) {
            String tmp = parts[0];
            parts[0] = parts[parts.length - 1];
            parts[parts.length - 1] = tmp;
        }
        return String.join(" ", parts);
    }

    private static int fixQuantity(int quantity) {
        String reversed = new StringBuilder(String.valueOf(quantity)).reverse().toString();
        return Integer.parseInt(reversed);
    }
}
