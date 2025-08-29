package com.example.WebhookApplication.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SqlSolverService {

    private static final Logger logger = LoggerFactory.getLogger(SqlSolverService.class);

    public String getSolutionForRegistrationNumber(String regNo) {
        // Extract last two digits
        String lastTwoDigits = regNo.substring(regNo.length() - 2);
        int lastTwoDigitsInt;

        try {
            lastTwoDigitsInt = Integer.parseInt(lastTwoDigits);
        } catch (NumberFormatException e) {
            logger.error("Invalid registration number format: {}", regNo);
            return getDefaultSolution();
        }

        logger.info("Registration number: {}, Last two digits: {}, Type: {}",
                regNo, lastTwoDigits, (lastTwoDigitsInt % 2 == 0 ? "Even" : "Odd"));

        if (lastTwoDigitsInt % 2 == 0) {
            return getQuestion2Solution(); // Even
        } else {
            return getQuestion1Solution(); // Odd
        }
    }

    private String getQuestion1Solution() {
        // Question 1 solution - Complex query for employee analytics
        return """
            WITH DepartmentStats AS (
                SELECT 
                    department,
                    AVG(salary) as avg_salary,
                    COUNT(*) as emp_count
                FROM employees 
                GROUP BY department
            ),
            EmployeeRanks AS (
                SELECT 
                    e.*,
                    RANK() OVER (PARTITION BY e.department ORDER BY e.salary DESC) as salary_rank,
                    DENSE_RANK() OVER (ORDER BY e.salary DESC) as overall_rank
                FROM employees e
            )
            SELECT 
                er.employee_id,
                er.employee_name,
                er.department,
                er.salary,
                er.salary_rank,
                er.overall_rank,
                ds.avg_salary as dept_avg_salary,
                ROUND((er.salary - ds.avg_salary) / ds.avg_salary * 100, 2) as salary_deviation_pct
            FROM EmployeeRanks er
            JOIN DepartmentStats ds ON er.department = ds.department
            WHERE er.salary > ds.avg_salary
            ORDER BY er.department, er.salary_rank;
            """;
    }

    private String getQuestion2Solution() {
        // Question 2 solution - Customer order analytics
        return """
            WITH CustomerMetrics AS (
                SELECT 
                    c.customer_id,
                    c.customer_name,
                    c.registration_date,
                    COUNT(o.order_id) as total_orders,
                    SUM(o.order_amount) as total_spent,
                    AVG(o.order_amount) as avg_order_value,
                    MAX(o.order_date) as last_order_date,
                    MIN(o.order_date) as first_order_date
                FROM customers c
                LEFT JOIN orders o ON c.customer_id = o.customer_id
                WHERE o.order_date >= DATE_SUB(CURRENT_DATE, INTERVAL 1 YEAR)
                GROUP BY c.customer_id, c.customer_name, c.registration_date
            ),
            CustomerCategories AS (
                SELECT 
                    *,
                    CASE 
                        WHEN total_spent >= 10000 THEN 'Premium'
                        WHEN total_spent >= 5000 THEN 'Gold'
                        WHEN total_spent >= 1000 THEN 'Silver'
                        ELSE 'Bronze'
                    END as customer_tier,
                    DATEDIFF(last_order_date, first_order_date) as customer_lifespan_days
                FROM CustomerMetrics
                WHERE total_orders >= 3
            )
            SELECT 
                customer_id,
                customer_name,
                total_orders,
                total_spent,
                avg_order_value,
                customer_tier,
                customer_lifespan_days,
                RANK() OVER (ORDER BY total_spent DESC) as spending_rank
            FROM CustomerCategories
            WHERE customer_tier IN ('Premium', 'Gold')
            ORDER BY total_spent DESC, total_orders DESC;
            """;
    }

    private String getDefaultSolution() {
        // Fallback solution
        return """
            SELECT 
                id,
                name,
                email,
                created_date
            FROM users 
            WHERE active = 1
            ORDER BY created_date DESC
            LIMIT 100;
            """;
    }
}