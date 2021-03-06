package Execution;

import Management.Admin;
import Management.Caller;
import Management.Cloud.Dropbox;
import Management.Polling;
import Management.User;
import com.dropbox.core.DbxException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.security.Signature;
public class Main {
	//TODO se l'utente chiede conferma all'admin per la creaione del pwdfolder -> evito duplicati
	//todo problema: l'admin deve avere accesso a tutti i pwdfolder -> account critico
	public final static Path MY_TEMP_PATH = Paths.get(System.getProperty("java.io.tmpdir"));
	public final static Path BASE_PATH = Paths.get(System.getProperty("user.home"));
	public final static Path MY_PERSONAL_PATH = BASE_PATH.resolve("CryptoCloud");
	public final static String END_PUBLIC = ".public";
	public final static String END_PRIVATE = ".private";
	public final static String END_SIGNED = ".sign";
	public final static String END_ADMIN = ".admin";


	//this is not used for security but to avoid users to mess with files in the dropbox folders
	public static SecretKey secretkey = new SecretKeySpec("OssigenoCryptoCloudSimon".getBytes(), "AES");


	public static void successFunction(String nameFunction) {
		System.out.println(nameFunction + " completed.");
	}

	public static boolean verifyPkcs1Signature(PublicKey rsaPublic, byte[] input,
	                                           byte[] encSignature) {
		try {
			Signature signature = Signature.getInstance("SHA384withRSA");

			signature.initVerify(rsaPublic);
			signature.update(input);
			return signature.verify(encSignature);
		} catch (Exception e) {
			throw new Main.ExecutionException("verifySignature", e);

		}
	}

	public static void deleteLocalFiles(Path... paths) {
		for (Path path : paths) {
			try {
				Files.deleteIfExists(path);
			} catch (IOException e) {
				throw new ExecutionException("deleteLocalFiles", e);
			}

		}

	}

	public static String inputPassword() {
		String code;
		if (System.console() != null) {
			char[] chars = System.console().readPassword();
			code = new String(chars);
		} else {
			try {
				code = new BufferedReader(new InputStreamReader(System.in)).readLine();
			} catch (IOException e) {
				throw new Main.ExecutionException("verifySignature", e);
			}

		}
		while (code == null || code.equals("\n") || code.equals("\t")
				|| code.equals("")) {
			System.out.println("Please write something");
			code = inputPassword();
		}
		code = code.trim();
		return code;

	}

	public static String input() {
		try {
			String code = new BufferedReader(new InputStreamReader(System.in)).readLine();
			while (code == null || code.equals("\n") || code.equals("\t")
					|| code.equals("")) {
				System.out.println("Please write something");
				code = input();
			}
			code = code.trim();
			return code;
		} catch (IOException e) {
			throw new ExecutionException("input", e);
		}

	}

	public static void deleteDirectory(Path path) {
		if (Files.exists(path)) {
			try {
				Files.list(path).forEach(pathInternal -> {
					try {
						Files.deleteIfExists(pathInternal);
					} catch (IOException e) {
						throw new Main.ExecutionException("delete", e);
					}
				});
				Files.deleteIfExists(path);
			} catch (IOException e) {
				throw new Main.ExecutionException("deleteDirectory", e);
			}
		}
	}

	private static void test(String email) {
		User terry = new User.UserBuilder(email).setPublicKey().setVerified().build();
		System.out.println(terry.getEmail());
		System.out.println(terry.getPublicKey());
		System.out.println(terry.getVerified());
	}

	public static void main(String args[]) throws DbxException {
		Dropbox.initDropboxClient();
		Caller caller = new Caller(new User.UserBuilder(Dropbox.getCallerEmail()).setCaller());
		System.out.print("Welcome ");
		boolean change = false;
		if (Dropbox.isAdmin()) {
			System.out.println("Admin " + Dropbox.getClient().users().getCurrentAccount().getName().getDisplayName());
			Admin admin = new Admin(caller);
			Polling polling = admin.setupAdmin();
			change = manageInput(admin);
			polling.shutdown();
		}
		if (!Dropbox.isAdmin() || change) {
			System.out.println(Dropbox.getClient().users().getCurrentAccount().getName().getDisplayName());
			caller.setup();
			manageInput(caller);
		}
		System.out.println("Goodbye");
		System.exit(0);

	}

	private static boolean manageInput(Caller caller) {
		System.out.println("Please insert your command, 'help' for the list of possibilities");
		String input = input();
		while (!input.equals("exit")) {
			if (caller instanceof Admin) {
				switch (input) {
					case "help":
						help(caller);
						break;
					case "becomeUser":
						return true;
					case "signUser":
						((Admin) caller).signUser();
						successFunction("signUser");
						break;
					case "removeSignUser":
						((Admin) caller).designUser();
						successFunction("removeSignUser");
						break;
					case "listUsers":
						caller.listUsers().forEach(System.out::println);
						break;
					case "listGroups":
						caller.listGroups().forEach(System.out::println);
						break;
					case "signGroup":
						((Admin) caller).signGroup();
						successFunction("signGroup");
						break;
					case "removeSignGroup":
						((Admin) caller).designGroup();
						successFunction("removeSignGroup");
						break;
					case "addUsersToFileSystem":
						((Admin) caller).addUsersToFileSystem();
						successFunction("addUsersToFileSystem");
						break;
					case "removeUsersFromFileSystem":
						((Admin) caller).removeUsersFromFileSystem();
						successFunction("removeUsersFromFileSystem");
						break;
					default:
						System.err.println("Command not recognized");

				}

			} else {
				if (caller.getVerified()) {
					switch (input) {
						case "help":
							help(caller);
							break;
						case "recreateKeys":
							caller.reCreateKeys();
							successFunction("recreateKeys");
							break;
						case "mountSystem":
							caller.createFileSystem();
							successFunction("mountSystem");
							break;
						case "listUsers":
							caller.listUsers().forEach(System.out::println);
							break;
						case "listGroups":
							caller.listGroups().forEach(System.out::println);
							break;
						case "listOwningPwdFolders":
							caller.listPwdFoldersOwned().forEach(System.out::println);
							break;
						case "listAllPwdFolders":
							caller.listAllPwdFolders().forEach(System.out::println);
							break;
						case "listPwdEntries":
							caller.listPwdEntries().forEach(System.out::println);
							break;
						case "createGroup":
							caller.createGroup();
							successFunction("createGroup");
							break;
						case "addMembersToGroup":
							caller.addMembersToGroup();
							successFunction("addMembersToGroup");
							break;
						case "removeMembersFromGroup":
							caller.removeMembersFromGroup();
							successFunction("removeMembersFromGroup");
							break;
						case "deleteGroup":
							caller.deleteGroup();
							successFunction("deleteGroup");
							break;
						case "createPwdFolder":
							caller.createPwdFolder();
							successFunction("createPwdFolder");
							break;
						case "addGroupsToPwdFolder":
							caller.addGroupsToPwdFolder();
							successFunction("addGroupsToPwdFolder");
							break;
						case "removeGroupsFromPwdFolder":
							caller.removeGroupsFromPwdFolder();
							successFunction("removeGroupsFromPwdFolder");
							break;
						case "deletePwdFolder":
							caller.deletePwdFolder();
							successFunction("deletePwdFolder");
							break;
						case "createPwdEntry":
							caller.createPwdEntry();
							successFunction("createPwdEntry");
							break;
						case "showPwdEntry":
							caller.showPwdEntry();
							break;
						case "modifyPwdEntry":
							caller.modifyPwdEntry();
							successFunction("modifyPwdEntry");
							break;
						case "deletePwdEntry":
							caller.deletePwdEntry();
							successFunction("deletePwdEntry");
							break;
						case "connect":
							new Connection(caller).connect();
							successFunction("connect");
						default:
							System.err.println("Command not recognized");

					}
				} else {
					System.err.println("Please wait for the Admin signature");
					break;
				}

			}
			input = input();
		}
		return false;
	}

	private static void help(Caller caller) {
		System.out.println("There are the possible operations:");
		if (caller instanceof Admin) {
			System.out.println("becomeUser");
			System.out.println("listUsers");
			System.out.println("listGroups");
			System.out.println("signUser");
			System.out.println("removeSignUser");
			System.out.println("signGroup");
			System.out.println("removeSignGroup");
			System.out.println("addUsersToFileSystem");
			System.out.println("removeUsersFromFileSystem");
		} else {
			System.out.println("connect");
			System.out.println("recreateKeys");
			System.out.println("mountSystem");
			System.out.println("listUsers");
			System.out.println("listGroups");
			System.out.println("listAllPwdFolders");
			System.out.println("listOwningPwdFolders");
			System.out.println("listPwdEntries");
			System.out.println("createGroup");
			System.out.println("deleteGroup");
			System.out.println("addMembersToGroup");
			System.out.println("removeMembersFromGroup");
			System.out.println("createPwdFolder");
			System.out.println("deletePwdFolder");
			System.out.println("addGroupsToPwdFolder");
			System.out.println("removeGroupsFromPwdFolder");
			System.out.println("createPwdEntry");
			System.out.println("modifyPwdEntry");
			System.out.println("deletePwdEntry");
		}
		System.out.println("exit");

	}

	public static class ExecutionException extends RuntimeException {
		public ExecutionException(String functionName) {
			super("Unable to execute function " + functionName);
		}

		public ExecutionException(String functionName, Throwable cause) {
			super("Unable to execute function " + functionName, cause);
		}

		public ExecutionException(String functionName, Throwable cause, User caller) {
			super("The user " + caller + " was unable to execute function" + functionName, cause);
		}

		public ExecutionException(String functionName, Throwable cause, Object caller) {
			super("The object " + caller.toString() + " was unable to execute function" + functionName, cause);
		}

	}

}
