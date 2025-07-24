package com.baml.mav.aieutil

import com.baml.mav.aieutil.auth.PasswordResolver
import com.baml.mav.aieutil.cli.BaseDatabaseCliCommand
import com.baml.mav.aieutil.service.DatabaseService
import com.baml.mav.aieutil.service.ExecutionResult
import com.baml.mav.aieutil.service.ProcedureRequest
import com.baml.mav.aieutil.util.ExceptionUtils
import picocli.CommandLine.*
import picocli.CommandLine.Command

@Command(
    name = "exec-proc",
    mixinStandardHelpOptions = true,
    description = ["Vault-authenticated procedure execution"],
    version = ["1.0.0"],
    exitCodeOnInvalidInput = CommandLine.ExitCode.USAGE,
    exitCodeOnExecutionException = CommandLine.ExitCode.SOFTWARE
)
class ExecProcedureCmd : BaseDatabaseCliCommand() {

    @Parameters(index = "0", description = ["Stored procedure name (e.g., MAV_OWNER.TemplateTable.Onehadoop_proc)"], arity = "0..1")
    private var procedure: String? = null

    @Option(names = ["--input"], description = ["Input parameters (name:type:value,name:type:value)"])
    private var input: String? = null

    @Option(names = ["--output"], description = ["Output parameters (name:type,name:type)"])
    private var output: String? = null

    companion object {
        private fun createService() = DatabaseService(
            PasswordResolver { String(System.console().readPassword("Enter password: ")) }
        )
    }

    init {
        service = createService()
    }

    override fun call(): Int = try {
        val request = buildProcedureRequest()
        val result = service.execute(request)
        result.formatOutput(System.out)
        result.exitCode
    } catch (e: Exception) {
        ExceptionUtils.handleCliException(e, "execute procedure", System.err)
    }

    private fun buildProcedureRequest() = ProcedureRequest.builder()
        .type(type)
        .database(database)
        .user(user)
        .procedure(procedure)
        .input(input)
        .output(output)
        .vaultConfig(createVaultConfig())
        .build()
} 