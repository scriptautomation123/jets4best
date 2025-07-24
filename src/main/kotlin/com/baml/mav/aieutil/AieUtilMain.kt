package com.baml.mav.aieutil

import com.baml.mav.aieutil.util.ExceptionUtils
import com.baml.mav.aieutil.util.LoggingUtils
import picocli.CommandLine
import picocli.CommandLine.Command

@Command(
    name = "aieutil",
    mixinStandardHelpOptions = true,
    description = ["AIE Utility - Database and Vault CLI Tool"],
    version = ["1.0.0"],
    subcommands = [
        ExecProcedureCmd::class,
        ExecSqlCmd::class,
        ExecVaultClientCmd::class
    ]
)
class AieUtilMain

fun main(args: Array<String>) {
    try {
        LoggingUtils.logCliStartup(System.getProperty("java.home"))
        val cmd = CommandLine(AieUtilMain())
        cmd.setExecutionExceptionHandler(ExceptionUtils.ExecutionExceptionHandler())
        cmd.setParameterExceptionHandler(ExceptionUtils.ParameterExceptionHandler())
        System.exit(cmd.execute(*args))
    } catch (e: Exception) {
        LoggingUtils.logMinimalError(e)
        System.exit(CommandLine.ExitCode.SOFTWARE)
    }
} 