package com.baml.mav.aieutil;

public class AieUtilCli {
    public static void main(String[] args) {
        if (args.length > 0 && "print-yaml".equals(args[0])) {
            AieUtilCliService.printYamlConfig();
            return;
        }
        if (args.length > 0 && "print-connect".equals(args[0])) {
            AieUtilCliService.printSampleConnectString();
            return;
        }
        // CLI parsing
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }
        int i = 0;
        String type = "oracle";
        String db = null, user = null, pass = null, role = null, secret = null, ait = null, host = null;
        String input = null, output = null;
        String procedure = null, sql = null, script = null;

        while (i < args.length) {
            String arg = args[i];
            switch (arg) {
                case "-t": type = args[++i]; break;
                case "-d": db = args[++i]; break;
                case "-u": user = args[++i]; break;
                case "-p": pass = args[++i]; break;
                case "--role": role = args[++i]; break;
                case "--secret": secret = args[++i]; break;
                case "--ait": ait = args[++i]; break;
                case "--host": host = args[++i]; break;
                case "--input": input = args[++i]; break;
                case "--output": output = args[++i]; break;
                case "--sql": sql = args[++i]; break;
                case "--script": script = args[++i]; break;
                default:
                    if (procedure == null) procedure = arg;
                    else procedure += " " + arg;
                    break;
            }
            i++;
        }

        if (db == null || user == null) {
            System.err.println("Missing required arguments: -d and -u are required.");
            printUsage();
            System.exit(1);
        }



        // Decide what to run
        var params = new AieUtilCliService.CliParams(type, db, user, pass, role, secret, ait, host, procedure, sql, script, input, output);
        
        if (procedure != null) AieUtilCliService.runProcedure(params);
        else if (sql != null) AieUtilCliService.runSql(params);
        else if (script != null) AieUtilCliService.runSqlScript(params);
        else printUsage();
    }



    private static void printUsage() {
        System.out.println("""
AIE Util CLI

Usage:
  aieutil -t oracle -d dbname -u dbuser ["SCHEMA.PROCNAME"] [--input ...] [--output ...] [connection options]
  aieutil -t h2 -d dbname -u dbuser ["SCHEMA.PROCNAME"] [--input ...] [--output ...] [connection options]

Database types:
  oracle    Oracle Database (supports LDAP and JDBC thin)
  h2        H2 Database (in-memory or file-based)

Connection options:
  -p        Password (omit to prompt securely, or use Vault switches)
  --role    Vault role (for Vault mode)
  --secret  Vault secret (for Vault mode)
  --ait     Vault AIT (for Vault mode)
  --host    Database host (for JDBC mode)
  --input   (for procedure) Input params: PARAM:DATATYPE:VALUE,...
  --output  (for procedure) Output params: PARAM:DATATYPE,...

Oracle connection modes:
  If only -t, -d, -u provided → LDAP + Vault or prompt
  If -t, -d, -u, --host       → JDBC thin + Vault or prompt
  If -t, -d, -u, -p           → LDAP + direct password
  If -t, -d, -u, -p, --host   → JDBC thin + direct password

H2 connection modes:
  If only -t, -d, -u provided → In-memory database
  If -t, -d, -u, --host       → File-based database
""");
    }
}