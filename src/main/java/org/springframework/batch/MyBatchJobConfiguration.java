package org.springframework.batch;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.support.JdbcTransactionManager;

@Configuration
@EnableBatchProcessing
public class MyBatchJobConfiguration {

    @Bean
    public Step step(JobRepository jobRepository, JdbcTransactionManager transactionManager) {
        return new StepBuilder("step", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                System.out.println("Running step");

                throw new RuntimeException(); // fail -> so that we can restart
            }, transactionManager)
            .build();
    }

    @Bean
    public Job job(JobRepository jobRepository, Step step) {
        return new JobBuilder("job", jobRepository)
            .start(step)
            .build();
    }

    /*
     * Infrastructure beans configuration
     */

    @Bean
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .addScript("/org/springframework/batch/core/schema-h2.sql")
            .build();
    }

    @Bean
    public JdbcTransactionManager transactionManager(DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }

    /*
     * Main method to run the application and exhibit the issue
     */
    public static void main(String[] args) throws Exception {
        ApplicationContext context = new AnnotationConfigApplicationContext(MyBatchJobConfiguration.class);
        JobLauncher jobLauncher = context.getBean(JobLauncher.class);
        Job job = context.getBean(Job.class);
        final JobExplorer jobExplorer = context.getBean(JobExplorer.class);

        JobExecution jobExecution = jobLauncher.run(job, buildJobParameters("first"));

        printAllJobExecutions(jobExplorer, jobExecution);

        // Restart
        jobExecution = jobLauncher.run(job, buildJobParameters("second"));

        System.out.println(
            "Here I would expected that the 'exampleNonIdentifying' job parameter values are different for the two job executions, because the second job execution was started with a different 'exampleNonIdentifying' job parameter value.");
        System.out.println("But as we can see, the 'exampleNonIdentifying' job parameter values are the same!");
        printAllJobExecutions(jobExplorer, jobExecution);

    }

    private static void printAllJobExecutions(JobExplorer jobExplorer, JobExecution jobExecution) {
        jobExplorer.getJobExecutions(jobExecution.getJobInstance())
            .forEach(MyBatchJobConfiguration::printJobExecution);
    }

    private static JobParameters buildJobParameters(String txt) {
        final JobParametersBuilder jobParametersBuilder = new JobParametersBuilder();
        jobParametersBuilder.addLong("id", 1L, true);
        jobParametersBuilder.addString("exampleNonIdentifying", txt, false); // important here: this job parameter is non-identifying

        return jobParametersBuilder.toJobParameters();
    }

    private static void printJobExecution(JobExecution jobExecution) {
        System.out.printf("Execution ID: '%d', Parameters: '%s'%n", jobExecution.getId(), jobExecution.getJobParameters());
    }

}
