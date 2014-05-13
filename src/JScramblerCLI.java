/*
 * Copyright 2014, 2014 AuditMark
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the Lesser GPL
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
import com.auditmark.jscrambler.client.JScramblerFacade;

/**
 *
 * @author magalhas
 */
public class JScramblerCLI {
  public static void main(String[] args) {
    try {
      if (args.length < 1 || args.length > 1) {
        throw new Exception("Usage: java JScramblerCLI [path_to_config.json]");
      }
      JScramblerFacade.process(args[0]);
    } catch (Exception ex) {
      System.out.println(ex.toString());
    }
  }
}
