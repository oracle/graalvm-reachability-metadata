/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package grpc;

public interface HelloReplyOrBuilder extends
    // @@protoc_insertion_point(interface_extends:HelloReply)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string message = 1;</code>
   * @return The message.
   */
  java.lang.String getMessage();
  /**
   * <code>string message = 1;</code>
   * @return The bytes for message.
   */
  com.google.protobuf.ByteString
      getMessageBytes();
}
