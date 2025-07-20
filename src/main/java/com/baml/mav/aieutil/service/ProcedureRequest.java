package com.baml.mav.aieutil.service;

public final class ProcedureRequest extends DatabaseRequest {
    private final String procedure;
    private final String input;
    private final String output;

    private ProcedureRequest(Builder builder) {
        super(builder);
        this.procedure = builder.procedure;
        this.input = builder.input;
        this.output = builder.output;
    }

    // Getters
    public String getProcedure() {
        return procedure;
    }

    public String getInput() {
        return input;
    }

    public String getOutput() {
        return output;
    }

    public boolean isPasswordOnlyMode() {
        return procedure == null || procedure.trim().isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DatabaseRequest.Builder<Builder> {
        private String procedure;
        private String input;
        private String output;

        public Builder procedure(String procedure) {
            this.procedure = procedure;
            return this;
        }

        public Builder input(String input) {
            this.input = input;
            return this;
        }

        public Builder output(String output) {
            this.output = output;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ProcedureRequest build() {
            return new ProcedureRequest(this);
        }
    }
}
