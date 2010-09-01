/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rsna.isn.prepcontent.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.rsna.isn.prepcontent.domain.Exam;

/**
 *
 * @author wtellis
 */
public class ExamDao extends Dao
{
	public Exam getExam(int examId) throws ClassNotFoundException, SQLException
	{
		Connection con = getConnection();
		try
		{
			String select = "SELECT * from exam_status WHERE exam_id = " + examId;

			ResultSet rs = con.createStatement().executeQuery(select);
			if(rs.next())
			{
				Exam exam = new Exam();

				exam.setMrn(rs.getString("mrn"));
				exam.setAccNum(rs.getString("accession_number"));
				exam.setStatus(rs.getString("status"));
				exam.setStatusTimestamp(rs.getTimestamp("status_timestamp"));

				return exam;
			}

			return null;
		}
		finally
		{
			con.close();
		}
	}

}
