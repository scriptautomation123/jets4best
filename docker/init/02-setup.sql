-- Additional Oracle Database Setup
-- Configure session parameters and performance settings

-- Set session parameters for better development experience
ALTER SESSION SET NLS_DATE_FORMAT = 'YYYY-MM-DD HH24:MI:SS';
ALTER SESSION SET NLS_TIMESTAMP_FORMAT = 'YYYY-MM-DD HH24:MI:SS.FF';

-- Enable SQL tracing for debugging (optional)
-- ALTER SESSION SET SQL_TRACE = TRUE;

-- Create a simple test procedure
CREATE OR REPLACE PROCEDURE dev_user.test_procedure (
    p_name IN VARCHAR2,
    p_result OUT VARCHAR2
) AS
BEGIN
    p_result := 'Hello ' || p_name || ' from Oracle!';
END;
/

-- Grant execute permission
GRANT EXECUTE ON dev_user.test_procedure TO dev_user;

COMMIT; 