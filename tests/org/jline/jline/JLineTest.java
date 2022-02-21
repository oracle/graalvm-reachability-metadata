public class JLineTest {
    public static void main(String[] args) {
        if (args == null || args.length < 2) {
            System.err.println("Incorrect number of arguments");
            System.exit(1);
        }
        var libraryVersion = args[0];
        var jarFile = args[1];
        System.out.printf("Testing JLine version %s with config from jar file: %s. %n", libraryVersion, jarFile);
        System.exit(0);
    }
}
