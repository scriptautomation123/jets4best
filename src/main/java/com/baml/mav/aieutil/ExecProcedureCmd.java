package com.baml.mav.aieutil;

import com.baml.mav.aieutil.auth.PasswordResolver;
import com.baml.mav.aieutil.cli.BaseDatabaseCliCommand;
import com.baml.mav.aieutil.service.ExecutionResult;
import com.baml.mav.aieutil.service.ProcedureExecutionService;
import com.baml.mav.aieutil.service.ProcedureRequest;
import com.baml.mav.aieutil.util.ExceptionUtils;
import com.baml.mav.aieutil.util.LoggingUtils;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "exec-proc", mixinStandardHelpOptions = true, description = "Vault-authenticated procedure execution", version = "1.0.0", exitCodeOnInvalidInput = CommandLine.ExitCode.USAGE, exitCodeOnExecutionException = CommandLine.ExitCode.SOFTWARE)
public class ExecProcedureCmd extends BaseDatabaseCliCommand {

    // PicoCLI annotations only
    @Parameters(index = "0", description = "Stored procedure name (e.g., MAV_OWNER.TemplateTable.Onehadoop_proc)", arity = "0..1")
    String procedure;

    @Option(names = "--input", description = "Input parameters (name:type:value,name:type:value)")
    String input;

    @Option(names = "--output", description = "Output parameters (name:type,name:type)")
    String output;

    public ExecProcedureCmd() {
        super(createService());
    }

    private static ProcedureExecutionService createService() {
        return new ProcedureExecutionService(
                new PasswordResolver(new ExecProcedureCmd()::promptForPassword));
    }

    public static void main(String[] args) {
        try {
            LoggingUtils.logCliStartup(System.getProperty("java.home"));

            CommandLine cmd = new CommandLine(new ExecProcedureCmd());
            cmd.setExecutionExceptionHandler(new ExceptionUtils.ExecutionExceptionHandler());
            cmd.setParameterExceptionHandler(new ExceptionUtils.ParameterExceptionHandler());

            System.exit(cmd.execute(args)); // NOSONAR - CLI application must exit with appropriate code
        } catch (Exception e) {
            LoggingUtils.logMinimalError(e);
            System.exit(CommandLine.ExitCode.SOFTWARE); // NOSONAR - CLI application must exit with appropriate code
        }
    }

    @Override
    public Integer call() {
        try {
            ProcedureRequest request = ProcedureRequest.builder()
                    .type(type)
                    .database(database)
                    .user(user)
                    .procedure(procedure)
                    .input(input)
                    .output(output)
                    .vaultConfig(createVaultConfig())
                    .build();

            ExecutionResult result = service.execute(request);
            result.formatOutput(System.out); // NOSONAR
            return result.getExitCode();

        } catch (Exception e) {
            return ExceptionUtils.handleCliException(e, "execute procedure", System.err); // NOSONAR
        }
    }
}