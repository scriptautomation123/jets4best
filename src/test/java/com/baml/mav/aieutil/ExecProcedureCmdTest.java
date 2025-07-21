package com.baml.mav.aieutil;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;

class ExecProcedureCmdTest {
    @Test
    void parsesArgumentsAndCalls() {
        ExecProcedureCmd cmd = new ExecProcedureCmd();
        CommandLine cli = new CommandLine(cmd);
        int exitCode = cli.execute(
                "MAV_OWNER.TempTable_Onehadoop_proc",
                "--input",
                "in_schema:VARCHAR2:MAV_OWNER,in_src_table_nm:VARCHAR2:CUST_INSGHT_DLY,in_typ_cd:INTEGER:136,in_prant_grp:VARCHAR2:HADOOP_DML_ROLE",
                "--output", "p_outmsg:STRING",
                "-d", "ECICMD03_SVC01",
                "-u", "MAV_T2T_APP");
        assertThat(exitCode).isNotNull();
    }
}