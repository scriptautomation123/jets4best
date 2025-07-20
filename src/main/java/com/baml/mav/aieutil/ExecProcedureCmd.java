package com.baml.mav.aieutil;

import com.baml.mav.aieutil.auth.PasswordResolver;
import com.baml.mav.aieutil.cli.BaseDatabaseCliCommand;
import com.baml.mav.aieutil.service.DatabaseService;
import com.baml.mav.aieutil.service.ExecutionResult;
import com.baml.mav.aieutil.service.ProcedureRequest;
import com.baml.mav.aieutil.util.ExceptionUtils;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command-line interface for executing stored procedures with vault
 * authentication.
 * Supports both LDAP and JDBC connections with secure password resolution.
 */
@Command(name = "exec-proc", mixinStandardHelpOptions = true, description = "Vault-authenticated procedure execution", version = "1.0.0", exitCodeOnInvalidInput = CommandLine.ExitCode.USAGE, exitCodeOnExecutionException = CommandLine.ExitCode.SOFTWARE)
public class ExecProcedureCmd extends BaseDatabaseCliCommand {

  /** Stored procedure name to execute */
  @Parameters(index = "0", description = "Stored procedure name (e.g., MAV_OWNER.TemplateTable.Onehadoop_proc)", arity = "0..1")
  private String procedure;

  /** Input parameters in format name:type:value,name:type:value */
  @Option(names = "--input", description = "Input parameters (name:type:value,name:type:value)")
  private String input;

  /** Output parameters in format name:type,name:type */
  @Option(names = "--output", description = "Output parameters (name:type,name:type)")
  private String output;

  /**
   * Constructs a new ExecProcedureCmd with database service initialization.
   */
  public ExecProcedureCmd() {
    super(createService());
  }

  private static DatabaseService createService() {
    return new DatabaseService(new PasswordResolver(new ExecProcedureCmd()::promptForPassword));
  }

  @Override
  public Integer call() {
    try {
      final ProcedureRequest request = buildProcedureRequest();
      final ExecutionResult result = service.execute(request);
      result.formatOutput(System.out); // NOSONAR
      return result.getExitCode();

    } catch (Exception e) {
      return ExceptionUtils.handleCliException(e, "execute procedure", System.err); // NOSONAR
    }
  }

  private ProcedureRequest buildProcedureRequest() {
    return ProcedureRequest.builder()
        .type(type)
        .database(database)
        .user(user)
        .procedure(procedure)
        .input(input)
        .output(output)
        .vaultConfig(createVaultConfig())
        .build();
  }

}
